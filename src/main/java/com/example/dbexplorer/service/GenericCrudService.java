package com.example.dbexplorer.service;

import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import com.example.dbexplorer.config.CrudEntities.LookupDef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table-agnostic CRUD backed by the CrudEntities whitelist.
 * Column types are discovered once from JDBC metadata, so no per-table DTOs
 * or SQL are needed — rows travel as Map&lt;COLUMN_NAME, value&gt;.
 */
@Service
public class GenericCrudService {

    private final JdbcTemplate jdbc;

    /** table -> (COLUMN_NAME -> java.sql.Types constant), discovered lazily. */
    private final Map<String, Map<String, Integer>> typeCache = new ConcurrentHashMap<>();

    public GenericCrudService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── List / search ─────────────────────────────────────────────────────────

    public Map<String, Object> list(CrudEntity e, String search, int page, int pageSize) {
        String where = buildSearchWhere(e, search);
        long total = Optional.ofNullable(
            jdbc.queryForObject("SELECT COUNT(*) FROM " + e.table + where, Long.class)
        ).orElse(0L);

        String sql =
            "SELECT * FROM (" +
            "  SELECT a.*, ROWNUM rnum_ FROM (" +
            "    SELECT * FROM " + e.table + where + " ORDER BY " + e.defaultOrderBy +
            "  ) a WHERE ROWNUM <= " + ((page + 1) * pageSize) +
            ") WHERE rnum_ > " + (page * pageSize);

        List<Map<String, Object>> items = jdbc.query(sql, (rs, i) -> mapRow(rs));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", items);
        r.put("total", total);
        r.put("page", page);
        r.put("pageSize", pageSize);
        return r;
    }

    private String buildSearchWhere(CrudEntity e, String search) {
        if (search == null || search.trim().isEmpty() || e.searchCols.isEmpty()) return "";
        String q = search.trim().toUpperCase().replace("'", "''");
        List<String> conds = new ArrayList<>();
        for (String col : e.searchCols) {
            conds.add("UPPER(TO_CHAR(" + col + ")) LIKE '%" + q + "%'");
        }
        return " WHERE (" + String.join(" OR ", conds) + ")";
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    public Map<String, Object> get(CrudEntity e, String id) {
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT * FROM " + e.table + " WHERE " + e.pk + " = ?",
            (rs, i) -> mapRow(rs), coerce(e.table, e.pk, id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public Object create(CrudEntity e, Map<String, Object> values) {
        Map<String, Integer> types = columnTypes(e.table);
        Map<String, Object> row = filterColumns(values, types);

        long newId = nextValue(e.table, e.pk);
        row.put(e.pk, newId);
        if (e.orderCol != null && row.get(e.orderCol) == null) {
            row.put(e.orderCol, nextValue(e.table, e.orderCol));
        }
        row.remove(e.createdAtCol);
        row.remove(e.updatedAtCol);

        List<String> cols = new ArrayList<>(row.keySet());
        List<String> placeholders = new ArrayList<>(Collections.nCopies(cols.size(), "?"));
        List<Object> args = new ArrayList<>();
        for (String c : cols) args.add(coerce(e.table, c, row.get(c)));

        if (e.createdAtCol != null) { cols.add(e.createdAtCol); placeholders.add("SYSTIMESTAMP"); }
        if (e.updatedAtCol != null) { cols.add(e.updatedAtCol); placeholders.add("SYSTIMESTAMP"); }

        jdbc.update(
            "INSERT INTO " + e.table + " (" + String.join(", ", cols) + ") VALUES (" +
            String.join(", ", placeholders) + ")",
            args.toArray());
        return newId;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update(CrudEntity e, String id, Map<String, Object> values) {
        Map<String, Integer> types = columnTypes(e.table);
        Map<String, Object> row = filterColumns(values, types);
        row.remove(e.pk);
        row.remove(e.createdAtCol);
        row.remove(e.updatedAtCol);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> en : row.entrySet()) {
            sets.add(en.getKey() + " = ?");
            args.add(coerce(e.table, en.getKey(), en.getValue()));
        }
        if (e.updatedAtCol != null) sets.add(e.updatedAtCol + " = SYSTIMESTAMP");
        if (sets.isEmpty()) return;
        args.add(coerce(e.table, e.pk, id));

        jdbc.update(
            "UPDATE " + e.table + " SET " + String.join(", ", sets) +
            " WHERE " + e.pk + " = ?",
            args.toArray());
    }

    // ── Lookup (dropdown data) ────────────────────────────────────────────────

    public List<Map<String, Object>> lookup(CrudEntity e, String name) {
        LookupDef def = e.lookups.get(name);
        if (def == null) throw new IllegalArgumentException("Unknown lookup: " + name);
        return jdbc.query(
            "SELECT " + def.idCol + " AS id, " + def.labelCol + " AS label FROM " +
            def.table + " ORDER BY " + def.orderBy,
            (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getObject("id"));
                m.put("label", rs.getString("label"));
                return m;
            });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public long nextValue(String table, String column) {
        Long v = jdbc.queryForObject(
            "SELECT NVL(MAX(" + column + "), 0) + 1 FROM " + table, Long.class);
        return v == null ? 1L : v;
    }

    /** Only keep keys that are real columns of the table (case-insensitive). */
    private Map<String, Object> filterColumns(Map<String, Object> values, Map<String, Integer> types) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (values == null) return out;
        for (Map.Entry<String, Object> en : values.entrySet()) {
            String col = en.getKey() == null ? null : en.getKey().toUpperCase();
            if (col != null && types.containsKey(col)) out.put(col, en.getValue());
        }
        return out;
    }

    private Map<String, Integer> columnTypes(String table) {
        return typeCache.computeIfAbsent(table, t ->
            jdbc.query("SELECT * FROM " + t + " WHERE 1 = 0", rs -> {
                Map<String, Integer> m = new LinkedHashMap<>();
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    m.put(md.getColumnName(i).toUpperCase(), md.getColumnType(i));
                }
                return m;
            }));
    }

    /** Convert a JSON value to the JDBC type the column expects. */
    private Object coerce(String table, String column, Object value) {
        if (value == null) return null;
        Integer type = columnTypes(table).get(column.toUpperCase());
        if (type == null) return value;
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        switch (type) {
            case Types.NUMERIC: case Types.DECIMAL: case Types.INTEGER:
            case Types.BIGINT: case Types.SMALLINT: case Types.DOUBLE: case Types.FLOAT:
                if (value instanceof Number) return value;
                return new java.math.BigDecimal(s);
            case Types.DATE: case Types.TIMESTAMP: case Types.TIME:
                return parseTemporal(s);
            default:
                return value.toString();
        }
    }

    private Timestamp parseTemporal(String s) {
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String p : patterns) {
            try { return new Timestamp(new SimpleDateFormat(p).parse(s).getTime()); }
            catch (Exception ignored) {}
        }
        return null;
    }

    /** Row → map keyed by COLUMN_NAME; temporal values formatted as ISO strings. */
    private Map<String, Object> mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String name = md.getColumnName(i).toUpperCase();
            if ("RNUM_".equals(name)) continue;
            int type = md.getColumnType(i);
            if (type == Types.DATE || type == Types.TIMESTAMP) {
                Timestamp ts = rs.getTimestamp(i);
                row.put(name, ts == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(ts));
            } else {
                row.put(name, rs.getObject(i));
            }
        }
        return row;
    }
}
