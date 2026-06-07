package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.dto.Dtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchemaService {

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private String cachedSchema;

    public SchemaService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.cachedSchema = resolveSchema0();
    }

    /** Resolve the schema/owner to introspect. */
    public String resolveSchema() {
        return this.cachedSchema;
    }

    private String resolveSchema0() {
        if (StringUtils.hasText(props.getDefaultSchema())) {
            return props.getDefaultSchema().trim().toUpperCase();
        }
        return jdbc.queryForObject("SELECT USER FROM DUAL", String.class);
    }

    /** List all object names of a given category for the schema. */
    public List<DbObject> listObjects(String type) {
        String owner = resolveSchema();
        List<DbObject> out = new ArrayList<>();
        switch (type.toUpperCase()) {
            case "TABLE":
                jdbc.query(
                    "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name",
                    rs -> { out.add(new DbObject(rs.getString(1), "TABLE")); }, owner);
                break;
            case "VIEW":
                jdbc.query(
                    "SELECT view_name FROM all_views WHERE owner = ? ORDER BY view_name",
                    rs -> { out.add(new DbObject(rs.getString(1), "VIEW")); }, owner);
                break;
            case "PROCEDURE":
            case "FUNCTION":
            case "PACKAGE":
            case "TRIGGER":
                jdbc.query(
                    "SELECT object_name FROM all_objects WHERE owner = ? AND object_type = ? ORDER BY object_name",
                    rs -> { out.add(new DbObject(rs.getString(1), type.toUpperCase())); },
                    owner, type.toUpperCase());
                break;
            case "SEQUENCE":
                jdbc.query(
                    "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name",
                    rs -> { out.add(new DbObject(rs.getString(1), "SEQUENCE")); }, owner);
                break;
            default:
                throw new IllegalArgumentException("Unsupported object type: " + type);
        }
        return out;
    }

    /** Full detail for a table or view. */
    public TableDetail getTableDetail(String name) {
        String owner = resolveSchema();
        String objName = name.toUpperCase();

        TableDetail d = new TableDetail();
        d.owner = owner;
        d.name = objName;
        d.type = isView(owner, objName) ? "VIEW" : "TABLE";
        d.comments = tableComment(owner, objName);
        d.columns = loadColumns(owner, objName);
        d.constraints = loadConstraints(owner, objName);
        d.indexes = loadIndexes(owner, objName);
        d.rowCount = null; // counted on demand via the data endpoint
        return d;
    }

    private boolean isView(String owner, String name) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM all_views WHERE owner = ? AND view_name = ?",
            Integer.class, owner, name);
        return c != null && c > 0;
    }

    private String tableComment(String owner, String name) {
        List<String> r = jdbc.query(
            "SELECT comments FROM all_tab_comments WHERE owner = ? AND table_name = ?",
            (rs, i) -> rs.getString(1), owner, name);
        return r.isEmpty() ? null : r.get(0);
    }

    private List<ColumnInfo> loadColumns(String owner, String name) {
        // Primary-key columns first, so we can flag them.
        final Map<String, Boolean> pkCols = new LinkedHashMap<>();
        jdbc.query(
            "SELECT cc.column_name " +
            "FROM all_constraints c JOIN all_cons_columns cc " +
            "  ON c.owner = cc.owner AND c.constraint_name = cc.constraint_name " +
            "WHERE c.owner = ? AND c.table_name = ? AND c.constraint_type = 'P'",
            rs -> { pkCols.put(rs.getString(1), Boolean.TRUE); }, owner, name);

        final Map<String, String> colComments = new LinkedHashMap<>();
        jdbc.query(
            "SELECT column_name, comments FROM all_col_comments WHERE owner = ? AND table_name = ?",
            rs -> { colComments.put(rs.getString(1), rs.getString(2)); }, owner, name);

        List<ColumnInfo> cols = new ArrayList<>();
        jdbc.query(
            "SELECT column_name, data_type, data_length, data_precision, data_scale, " +
            "       nullable, data_default " +
            "FROM all_tab_columns WHERE owner = ? AND table_name = ? ORDER BY column_id",
            rs -> {
                ColumnInfo ci = new ColumnInfo();
                ci.name = rs.getString("column_name");
                ci.dataType = rs.getString("data_type");
                ci.dataLength = (Integer) nullableInt(rs.getObject("data_length"));
                ci.dataPrecision = (Integer) nullableInt(rs.getObject("data_precision"));
                ci.dataScale = (Integer) nullableInt(rs.getObject("data_scale"));
                ci.nullable = "Y".equals(rs.getString("nullable"));
                String def = rs.getString("data_default");
                ci.defaultValue = def == null ? null : def.trim();
                ci.primaryKey = pkCols.containsKey(ci.name);
                ci.comments = colComments.get(ci.name);
                cols.add(ci);
            }, owner, name);
        return cols;
    }

    private List<ConstraintInfo> loadConstraints(String owner, String name) {
        // Map constraint -> ordered columns
        final Map<String, List<String>> consCols = new LinkedHashMap<>();
        jdbc.query(
            "SELECT constraint_name, column_name FROM all_cons_columns " +
            "WHERE owner = ? AND table_name = ? ORDER BY constraint_name, position",
            rs -> {
                consCols.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(rs.getString(2));
            }, owner, name);

        List<ConstraintInfo> list = new ArrayList<>();
        jdbc.query(
            "SELECT c.constraint_name, c.constraint_type, c.r_constraint_name, c.search_condition " +
            "FROM all_constraints c WHERE c.owner = ? AND c.table_name = ? " +
            "AND c.constraint_type IN ('P','R','U','C') ORDER BY c.constraint_type, c.constraint_name",
            rs -> {
                ConstraintInfo c = new ConstraintInfo();
                c.name = rs.getString("constraint_name");
                c.type = rs.getString("constraint_type");
                List<String> cols = consCols.get(c.name);
                c.columns = cols == null ? "" : String.join(", ", cols);
                if ("R".equals(c.type)) {
                    String rConsName = rs.getString("r_constraint_name");
                    fillRefInfo(owner, c, rConsName);
                }
                if ("C".equals(c.type)) {
                    c.searchCondition = rs.getString("search_condition");
                }
                list.add(c);
            }, owner, name);
        return list;
    }

    private void fillRefInfo(String owner, ConstraintInfo c, String rConsName) {
        if (rConsName == null) return;
        jdbc.query(
            "SELECT table_name FROM all_constraints WHERE owner = ? AND constraint_name = ?",
            rs -> { c.refTable = rs.getString(1); }, owner, rConsName);
        List<String> refCols = new ArrayList<>();
        jdbc.query(
            "SELECT column_name FROM all_cons_columns WHERE owner = ? AND constraint_name = ? ORDER BY position",
            rs -> { refCols.add(rs.getString(1)); }, owner, rConsName);
        c.refColumns = String.join(", ", refCols);
    }

    private List<IndexInfo> loadIndexes(String owner, String name) {
        final Map<String, IndexInfo> byName = new LinkedHashMap<>();
        final Map<String, List<String>> idxCols = new LinkedHashMap<>();
        jdbc.query(
            "SELECT i.index_name, i.uniqueness, ic.column_name " +
            "FROM all_indexes i JOIN all_ind_columns ic " +
            "  ON i.index_name = ic.index_name AND i.owner = ic.index_owner " +
            "WHERE i.table_owner = ? AND i.table_name = ? " +
            "ORDER BY i.index_name, ic.column_position",
            rs -> {
                String idx = rs.getString("index_name");
                IndexInfo info = byName.computeIfAbsent(idx, k -> {
                    IndexInfo ii = new IndexInfo();
                    ii.name = idx;
                    ii.unique = false;
                    return ii;
                });
                info.unique = "UNIQUE".equals(rs.getString("uniqueness"));
                idxCols.computeIfAbsent(idx, k -> new ArrayList<>()).add(rs.getString("column_name"));
            }, owner, name);

        List<IndexInfo> out = new ArrayList<>(byName.values());
        for (IndexInfo ii : out) {
            ii.columns = String.join(", ", idxCols.getOrDefault(ii.name, new ArrayList<>()));
        }
        return out;
    }

    /** Source code (DDL text) for procedures/functions/packages/triggers/views. */
    public String getSource(String name, String type) {
        String owner = resolveSchema();
        String objName = name.toUpperCase();
        String t = type.toUpperCase();

        if ("VIEW".equals(t)) {
            List<String> r = jdbc.query(
                "SELECT text FROM all_views WHERE owner = ? AND view_name = ?",
                (rs, i) -> rs.getString(1), owner, objName);
            return r.isEmpty() ? "" : "CREATE OR REPLACE VIEW " + objName + " AS\n" + r.get(0);
        }

        StringBuilder sb = new StringBuilder();
        jdbc.query(
            "SELECT text FROM all_source WHERE owner = ? AND name = ? AND type = ? ORDER BY line",
            rs -> { sb.append(rs.getString(1)); }, owner, objName, t);
        return sb.toString();
    }

    /** Column names (uppercase) for a table/view — used to validate CRUD payloads. */
    public java.util.Set<String> getColumnNames(String name) {
        String owner = resolveSchema();
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        jdbc.query(
            "SELECT column_name FROM all_tab_columns WHERE owner = ? AND table_name = ? ORDER BY column_id",
            rs -> { set.add(rs.getString(1).toUpperCase()); }, owner, name.toUpperCase());
        return set;
    }

    /** Primary-key column names (uppercase) for a table. */
    public List<String> getPkColumns(String name) {
        String owner = resolveSchema();
        List<String> pk = new ArrayList<>();
        jdbc.query(
            "SELECT cc.column_name " +
            "FROM all_constraints c JOIN all_cons_columns cc " +
            "  ON c.owner = cc.owner AND c.constraint_name = cc.constraint_name " +
            "WHERE c.owner = ? AND c.table_name = ? AND c.constraint_type = 'P' " +
            "ORDER BY cc.position",
            rs -> { pk.add(rs.getString(1).toUpperCase()); }, owner, name.toUpperCase());
        return pk;
    }

    /** Oracle data_type for a single column (uppercase). Returns null if not found. */
    public String getColumnDataType(String table, String column) {
        String owner = resolveSchema();
        List<String> r = jdbc.query(
            "SELECT data_type FROM all_tab_columns WHERE owner = ? AND table_name = ? AND column_name = ?",
            (rs, i) -> rs.getString(1), owner, table.toUpperCase(), column.toUpperCase());
        return r.isEmpty() ? null : r.get(0).toUpperCase();
    }

    /** Count of objects per category — used for the sidebar badges. */
    public Map<String, Integer> objectCounts() {
        String owner = resolveSchema();
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("TABLE", count("SELECT COUNT(*) FROM all_tables WHERE owner = ?", owner));
        m.put("VIEW", count("SELECT COUNT(*) FROM all_views WHERE owner = ?", owner));
        m.put("PROCEDURE", countObj(owner, "PROCEDURE"));
        m.put("FUNCTION", countObj(owner, "FUNCTION"));
        m.put("PACKAGE", countObj(owner, "PACKAGE"));
        m.put("TRIGGER", countObj(owner, "TRIGGER"));
        m.put("SEQUENCE", count("SELECT COUNT(*) FROM all_sequences WHERE sequence_owner = ?", owner));
        return m;
    }

    private int count(String sql, String owner) {
        Integer c = jdbc.queryForObject(sql, Integer.class, owner);
        return c == null ? 0 : c;
    }

    /**
     * Lightweight change-detection fingerprint for the schema.
     * Combines the most recent DDL time with the total object count, so any
     * create / alter / drop in the schema changes the value.
     */
    public Map<String, Object> fingerprint() {
        String owner = resolveSchema();
        Map<String, Object> m = new LinkedHashMap<>();
        jdbc.query(
            "SELECT MAX(last_ddl_time) AS last_change, COUNT(*) AS obj_count " +
            "FROM all_objects WHERE owner = ?",
            rs -> {
                java.sql.Timestamp ts = rs.getTimestamp("last_change");
                m.put("lastChange", ts == null ? null : ts.toString());
                m.put("lastChangeMillis", ts == null ? 0L : ts.getTime());
                m.put("objectCount", rs.getInt("obj_count"));
            }, owner);
        m.put("fingerprint", String.valueOf(m.get("lastChangeMillis")) + ":" + m.get("objectCount"));
        m.put("checkedAt", System.currentTimeMillis());
        return m;
    }

    private int countObj(String owner, String type) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM all_objects WHERE owner = ? AND object_type = ?",
            Integer.class, owner, type);
        return c == null ? 0 : c;
    }

    private static Object nullableInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        return null;
    }
}
