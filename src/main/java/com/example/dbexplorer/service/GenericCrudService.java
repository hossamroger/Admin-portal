package com.example.dbexplorer.service;

import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import com.example.dbexplorer.config.CrudEntities.LookupDef;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table-agnostic CRUD backed by the CrudEntities whitelist.
 * Column types are discovered from JDBC metadata (cached with a TTL so
 * ALTER TABLE is picked up without a restart) — rows travel as
 * Map&lt;COLUMN_NAME, value&gt;.
 */
@Service
public class GenericCrudService {

    private static final long META_TTL_MS = 5 * 60_000L;
    private static final int  CREATE_RETRIES = 3;
    private static final int  LOOKUP_MAX_ROWS = 1000;

    private final JdbcTemplate jdbc;

    /** Per-column metadata discovered from the driver. */
    static final class ColMeta {
        final int type; final boolean nullable; final int size;
        ColMeta(int type, boolean nullable, int size) {
            this.type = type; this.nullable = nullable; this.size = size;
        }
    }
    private static final class TableMeta {
        final Map<String, ColMeta> cols; final long loadedAt;
        TableMeta(Map<String, ColMeta> cols) { this.cols = cols; this.loadedAt = System.currentTimeMillis(); }
    }

    /** Cached result of probing for a {@code <table>_SEQ} sequence (name may be null). */
    private static final class SeqProbe {
        final String name; final long loadedAt;
        SeqProbe(String name) { this.name = name; this.loadedAt = System.currentTimeMillis(); }
    }

    private final Map<String, TableMeta> metaCache = new ConcurrentHashMap<>();
    private final Map<String, SeqProbe> seqCache = new ConcurrentHashMap<>();

    /** entity slug → hook; empty when no per-entity hooks are registered. */
    private final Map<String, CrudHook> hooks;

    public GenericCrudService(JdbcTemplate jdbc) {
        this(jdbc, Collections.emptyList());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GenericCrudService(JdbcTemplate jdbc, List<CrudHook> hookBeans) {
        this.jdbc = jdbc;
        Map<String, CrudHook> m = new HashMap<>();
        for (CrudHook h : hookBeans) m.put(h.entity(), h);
        this.hooks = m;
    }

    // ── List / search ─────────────────────────────────────────────────────────

    /**
     * @param rowFilter optional per-user row-level filter condition (from
     *                  DBX_USER_TABLE_FILTERS), ANDed into the WHERE clause.
     * @param sort      optional sort column — validated against real columns.
     */
    public Map<String, Object> list(CrudEntity e, String search, int page, int pageSize,
                                    String rowFilter, String sort, String dir) {
        List<String> conds = new ArrayList<>();
        List<Object> args  = new ArrayList<>();

        if (rowFilter != null && !rowFilter.trim().isEmpty()) {
            conds.add("(" + rowFilter + ")");
        }
        if (search != null && !search.trim().isEmpty() && !e.searchCols.isEmpty()) {
            // escape LIKE wildcards so "%"/"_" are searched literally
            String esc = search.trim().toUpperCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String q = "%" + esc + "%";
            List<String> like = new ArrayList<>();
            for (String col : e.searchCols) {
                like.add("UPPER(TO_CHAR(" + col + ")) LIKE ? ESCAPE '\\'");
                args.add(q);
            }
            conds.add("(" + String.join(" OR ", like) + ")");
        }
        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
        Object[] whereArgs = args.toArray();

        String orderBy = e.defaultOrderBy;
        if (sort != null && !sort.trim().isEmpty()) {
            String col = sort.trim().toUpperCase();
            if (!columnMeta(e.table).containsKey(col))
                throw new IllegalArgumentException("Unknown sort column: " + sort);
            orderBy = col + ("DESC".equalsIgnoreCase(dir) ? " DESC" : " ASC") + ", " + e.pk;
        }

        long total = Optional.ofNullable(
            jdbc.queryForObject("SELECT COUNT(*) FROM " + e.table + where, Long.class, whereArgs)
        ).orElse(0L);

        String sql =
            "SELECT * FROM (" +
            "  SELECT a.*, ROWNUM rnum_ FROM (" +
            "    SELECT * FROM " + e.table + where + " ORDER BY " + orderBy +
            "  ) a WHERE ROWNUM <= " + ((page + 1L) * pageSize) +
            ") WHERE rnum_ > " + ((long) page * pageSize);

        List<Map<String, Object>> items = jdbc.query(sql, (rs, i) -> mapRow(rs), whereArgs);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", items);
        r.put("total", total);
        r.put("page", page);
        r.put("pageSize", pageSize);
        return r;
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    public Map<String, Object> get(CrudEntity e, String id, String rowFilter) {
        String where = " WHERE " + e.pk + " = ?";
        if (rowFilter != null && !rowFilter.trim().isEmpty()) where += " AND (" + rowFilter + ")";
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT * FROM " + e.table + where,
            (rs, i) -> mapRow(rs), coerce(e.table, e.pk, id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Object create(CrudEntity e, Map<String, Object> values) {
        Map<String, ColMeta> meta = columnMeta(e.table);
        Map<String, Object> row = filterColumns(e, values, meta);
        row.remove(e.pk);
        row.remove(e.createdAtCol);
        row.remove(e.updatedAtCol);
        validate(e, row, meta, true);

        CrudHook hook = hooks.get(e.name);
        if (hook != null) hook.beforeInsert(e, row);

        // Preferred path: if a conventional <table>_SEQ sequence exists, allocate
        // the id atomically with NEXTVAL — no race, no retry. (Run
        // db/create_pk_sequences.sql to enable this for the managed tables.)
        String seq = sequenceFor(e.table);
        if (seq != null) {
            long newId = Optional.ofNullable(
                jdbc.queryForObject("SELECT " + seq + ".NEXTVAL FROM DUAL", Long.class)).orElse(1L);
            long actualId = insertRow(e, row, newId);
            if (hook != null) hook.afterInsert(e, row, actualId);
            return actualId;
        }

        // Fallback: MAX+1 can race with a concurrent insert — retry with a fresh id on collision
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt < CREATE_RETRIES; attempt++) {
            long newId = nextValue(e.table, e.pk);
            try {
                long actualId = insertRow(e, row, newId);
                if (hook != null) hook.afterInsert(e, row, actualId);
                return actualId;
            } catch (DuplicateKeyException dup) {
                last = dup;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Could not allocate a unique ID — please retry", last);
    }

    /**
     * Returns the name of the PK sequence for a table if one exists in the current
     * schema following the {@code <table>_SEQ} convention, else null. Result is
     * cached (positive and negative) with the same TTL as column metadata, so a
     * newly-created sequence is picked up within {@value #META_TTL_MS} ms.
     */
    private String sequenceFor(String table) {
        String key = table.toUpperCase();
        SeqProbe cached = seqCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt < META_TTL_MS) {
            return cached.name;
        }
        String candidate = key + "_SEQ";
        String resolved = null;
        try {
            Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_sequences WHERE sequence_name = ?", Integer.class, candidate);
            if (n != null && n > 0) resolved = candidate;
        } catch (Exception ignore) {
            // user_sequences not accessible — stay on the MAX+1 fallback
        }
        seqCache.put(key, new SeqProbe(resolved));
        return resolved;
    }

    /**
     * Inserts the row and returns the PK value that was actually persisted.
     * We pre-fill {@code e.pk} with the allocated {@code newId}, but a BEFORE
     * INSERT trigger may reassign it (a common Oracle pattern). Capturing the
     * value via the RETURNING clause (JDBC generated keys for {@code e.pk})
     * means callers always get the real id, whether it came from our allocation
     * or from a trigger — so sub-resource saves can find the parent afterwards.
     */
    private long insertRow(CrudEntity e, Map<String, Object> row, long newId) {
        Map<String, Object> r = new LinkedHashMap<>(row);
        r.put(e.pk, newId);
        if (e.orderCol != null && r.get(e.orderCol) == null) {
            r.put(e.orderCol, nextValue(e.table, e.orderCol));
        }

        List<String> cols = new ArrayList<>(r.keySet());
        List<String> placeholders = new ArrayList<>(Collections.nCopies(cols.size(), "?"));
        List<Object> args = new ArrayList<>();
        for (String c : cols) args.add(coerce(e.table, c, r.get(c)));

        if (e.createdAtCol != null) { cols.add(e.createdAtCol); placeholders.add("SYSTIMESTAMP"); }
        if (e.updatedAtCol != null) { cols.add(e.updatedAtCol); placeholders.add("SYSTIMESTAMP"); }

        String sql = "INSERT INTO " + e.table + " (" + String.join(", ", cols) + ") VALUES (" +
            String.join(", ", placeholders) + ")";
        String pkCol = e.pk;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{ pkCol });
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            return ps;
        }, kh);

        Number key = keyOrNull(kh);
        return key != null ? key.longValue() : newId;
    }

    /** Best-effort extraction of the single generated PK; null if unavailable. */
    private static Number keyOrNull(KeyHolder kh) {
        try {
            Object k = kh.getKey();
            if (k instanceof Number) return (Number) k;
        } catch (Exception ignore) {
            // Multiple/no keys or driver quirk — fall back to the allocated id.
        }
        return null;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public void update(CrudEntity e, String id, Map<String, Object> values, String rowFilter) {
        Map<String, ColMeta> meta = columnMeta(e.table);

        // Optimistic locking: if the client echoes the audit timestamp it loaded,
        // reject the save when someone else updated the row in the meantime.
        if (e.updatedAtCol != null && values != null && values.get(e.updatedAtCol) != null) {
            Map<String, Object> current = get(e, id, rowFilter);
            if (current == null) throw new NoSuchElementException("Record not found: " + id);
            Object dbVal = current.get(e.updatedAtCol);
            if (dbVal != null && !String.valueOf(dbVal).equals(String.valueOf(values.get(e.updatedAtCol)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This record was modified by someone else — reload and try again");
            }
        }

        Map<String, Object> row = filterColumns(e, values, meta);
        row.remove(e.pk);
        row.remove(e.createdAtCol);
        row.remove(e.updatedAtCol);
        validate(e, row, meta, false);

        CrudHook hook = hooks.get(e.name);
        if (hook != null) hook.beforeUpdate(e, id, row);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> en : row.entrySet()) {
            sets.add(en.getKey() + " = ?");
            args.add(coerce(e.table, en.getKey(), en.getValue()));
        }
        if (e.updatedAtCol != null) sets.add(e.updatedAtCol + " = SYSTIMESTAMP");
        if (sets.isEmpty()) return;
        args.add(coerce(e.table, e.pk, id));

        String where = " WHERE " + e.pk + " = ?";
        if (rowFilter != null && !rowFilter.trim().isEmpty()) where += " AND (" + rowFilter + ")";

        int updated = jdbc.update(
            "UPDATE " + e.table + " SET " + String.join(", ", sets) + where,
            args.toArray());
        if (updated == 0) throw new NoSuchElementException("Record not found: " + id);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(CrudEntity e, String id, String rowFilter) {
        CrudHook hook = hooks.get(e.name);
        if (hook != null) hook.beforeDelete(e, id);

        String where = " WHERE " + e.pk + " = ?";
        if (rowFilter != null && !rowFilter.trim().isEmpty()) where += " AND (" + rowFilter + ")";
        int deleted = jdbc.update(
            "DELETE FROM " + e.table + where,
            coerce(e.table, e.pk, id));
        if (deleted == 0) throw new NoSuchElementException("Record not found: " + id);
    }

    // ── Metadata (config-driven UI contract) ──────────────────────────────────

    /**
     * Describes an entity for clients: the registry-declared shape (pk, ops,
     * search columns, lookups, audit/order columns) merged with the live
     * driver-discovered column list (name, JDBC type, nullability, size).
     */
    public Map<String, Object> describe(CrudEntity e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", e.name);
        out.put("table", e.table);
        out.put("pk", e.pk);
        out.put("ops", new ArrayList<>(e.ops));
        out.put("searchCols", e.searchCols);
        out.put("orderCol", e.orderCol);
        out.put("createdAtCol", e.createdAtCol);
        out.put("updatedAtCol", e.updatedAtCol);
        out.put("lookups", new ArrayList<>(e.lookups.keySet()));

        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map.Entry<String, ColMeta> en : columnMeta(e.table).entrySet()) {
            ColMeta c = en.getValue();
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", en.getKey());
            col.put("jdbcType", c.type);
            col.put("nullable", c.nullable);
            col.put("size", c.size);
            boolean writable = e.writableCols.isEmpty()
                ? !en.getKey().equals(e.pk)
                : e.writableCols.contains(en.getKey());
            col.put("writable", writable);
            columns.add(col);
        }
        out.put("columns", columns);
        return out;
    }

    // ── Lookup (dropdown data) ────────────────────────────────────────────────

    public List<Map<String, Object>> lookup(CrudEntity e, String name) {
        LookupDef def = e.lookups.get(name);
        if (def == null) throw new IllegalArgumentException("Unknown lookup: " + name);
        return jdbc.query(
            "SELECT * FROM (SELECT " + def.idCol + " AS id, " + def.labelCol + " AS label FROM " +
            def.table + " ORDER BY " + def.orderBy + ") WHERE ROWNUM <= " + LOOKUP_MAX_ROWS,
            (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getObject("id"));
                m.put("label", rs.getString("label"));
                return m;
            });
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Driver-metadata validation: explicitly blank values on NOT NULL columns are
     * rejected (missing columns are left to the DB — they may have defaults), and
     * character values must fit the column size. Produces a 400 with a per-column
     * message instead of a raw ORA- error.
     */
    private void validate(CrudEntity e, Map<String, Object> row, Map<String, ColMeta> meta, boolean insert) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, ColMeta> en : meta.entrySet()) {
            String col = en.getKey();
            ColMeta m = en.getValue();
            if (col.equals(e.pk) || col.equals(e.createdAtCol) || col.equals(e.updatedAtCol)
                || col.equals(e.orderCol)) continue;
            if (!row.containsKey(col)) {
                // On INSERT a NOT NULL column simply left out of the payload would
                // otherwise surface as a raw DB constraint error (500). Only enforce
                // for columns the API can actually write.
                boolean writable = e.writableCols.isEmpty() || e.writableCols.contains(col);
                if (insert && !m.nullable && writable) problems.add(col + " is required");
                continue;
            }
            Object v = row.get(col);
            boolean blank = v == null || v.toString().trim().isEmpty();
            if (!m.nullable && blank) {
                problems.add(col + " is required");
                continue;
            }
            if (!blank && isCharType(m.type) && m.size > 0 && v.toString().length() > m.size) {
                problems.add(col + " exceeds maximum length of " + m.size);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join("; ", problems));
        }
    }

    private static boolean isCharType(int t) {
        return t == Types.VARCHAR || t == Types.CHAR || t == Types.NVARCHAR
            || t == Types.NCHAR || t == Types.LONGVARCHAR;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public long nextValue(String table, String column) {
        Long v = jdbc.queryForObject(
            "SELECT NVL(MAX(" + column + "), 0) + 1 FROM " + table, Long.class);
        return v == null ? 1L : v;
    }

    /**
     * Keep only keys that are real columns (case-insensitive) AND, when the
     * entity declares writableCols, only the columns it explicitly exposes —
     * blocks mass-assignment of unexposed columns.
     */
    private Map<String, Object> filterColumns(CrudEntity e, Map<String, Object> values,
                                              Map<String, ColMeta> meta) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (values == null) return out;
        for (Map.Entry<String, Object> en : values.entrySet()) {
            String col = en.getKey() == null ? null : en.getKey().toUpperCase();
            if (col == null || !meta.containsKey(col)) continue;
            if (!e.writableCols.isEmpty() && !e.writableCols.contains(col)
                && !col.equals(e.pk) && !col.equals(e.createdAtCol) && !col.equals(e.updatedAtCol)) continue;
            out.put(col, en.getValue());
        }
        return out;
    }

    private Map<String, ColMeta> columnMeta(String table) {
        TableMeta cached = metaCache.get(table);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt < META_TTL_MS) {
            return cached.cols;
        }
        Map<String, ColMeta> cols = jdbc.query("SELECT * FROM " + table + " WHERE 1 = 0", rs -> {
            Map<String, ColMeta> m = new LinkedHashMap<>();
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                m.put(md.getColumnName(i).toUpperCase(), new ColMeta(
                    md.getColumnType(i),
                    md.isNullable(i) != ResultSetMetaData.columnNoNulls,
                    md.getPrecision(i)));
            }
            return m;
        });
        metaCache.put(table, new TableMeta(cols));
        return cols;
    }

    /** Convert a JSON value to the JDBC type the column expects. Bad input → 400. */
    Object coerce(String table, String column, Object value) {
        if (value == null) return null;
        ColMeta m = columnMeta(table).get(column.toUpperCase());
        if (m == null) return value;
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        switch (m.type) {
            case Types.NUMERIC: case Types.DECIMAL: case Types.INTEGER:
            case Types.BIGINT: case Types.SMALLINT: case Types.DOUBLE: case Types.FLOAT:
                if (value instanceof Number) return value;
                try { return new java.math.BigDecimal(s); }
                catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid number for " + column + ": " + s);
                }
            case Types.DATE: case Types.TIMESTAMP: case Types.TIME:
                return parseTemporal(column, s);
            default:
                return value.toString();
        }
    }

    /** Strict (non-lenient) date parsing — garbage and rolled-over dates → 400. */
    static Timestamp parseTemporal(String column, String s) {
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p);
                f.setLenient(false);
                java.text.ParsePosition pos = new java.text.ParsePosition(0);
                java.util.Date d = f.parse(s, pos);
                if (d != null && pos.getIndex() == s.length()) return new Timestamp(d.getTime());
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Invalid date/time for " + column + ": " + s);
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
