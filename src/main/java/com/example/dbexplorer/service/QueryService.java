package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.dto.QueryDtos.ColumnFilter;
import com.example.dbexplorer.dto.QueryDtos.QueryResult;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class QueryService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryService.class);

    private final DataSource dataSource;
    private final AppProperties props;
    private final SchemaService schema;

    public QueryService(DataSource dataSource, AppProperties props, SchemaService schema) {
        this.dataSource = dataSource;
        this.props = props;
        this.schema = schema;
    }

    /** Execute a (possibly multi-statement) SQL script. Returns one result per statement. */
    public List<QueryResult> runScript(String script, Integer maxRowsOverride) {
        int maxRows = maxRowsOverride != null && maxRowsOverride > 0
                ? Math.min(maxRowsOverride, props.getMaxRows())
                : props.getMaxRows();

        List<String> statements = SqlSplitter.split(script);
        List<QueryResult> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            for (String stmt : statements) {
                if (stmt.trim().isEmpty()) continue;
                results.add(execOne(conn, stmt, maxRows));
            }
        } catch (SQLException e) {
            QueryResult r = new QueryResult();
            r.error = "Connection error: " + e.getMessage();
            results.add(r);
        }
        return results;
    }

    private QueryResult execOne(Connection conn, String sql, int maxRows) {
        QueryResult r = new QueryResult();
        r.statement = sql.length() > 500 ? sql.substring(0, 500) + " ..." : sql;
        long start = System.currentTimeMillis();

        // Enforce read-only mode.
        if (props.isReadOnly() && !isReadOnlyStatement(sql)) {
            r.error = "Read-only mode is enabled. Only SELECT statements are permitted.";
            return r;
        }

        try (Statement st = conn.createStatement()) {
            if (props.getQueryTimeoutSeconds() > 0) {
                st.setQueryTimeout(props.getQueryTimeoutSeconds());
            }
            st.setMaxRows(maxRows + 1); // fetch one extra to detect truncation

            boolean hasRs = st.execute(sql);
            if (hasRs) {
                try (ResultSet rs = st.getResultSet()) {
                    fillResultSet(r, rs, maxRows);
                    r.resultSet = true;
                }
            } else {
                r.resultSet = false;
                r.updateCount = st.getUpdateCount();
            }
        } catch (SQLException e) {
            log.warn("SQL error for stmt [{}]: {}", r.statement, e.getMessage());
            r.error = "Query execution failed.";
        }
        r.elapsedMs = System.currentTimeMillis() - start;
        return r;
    }

    private void fillResultSet(QueryResult r, ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        List<String> colNames = new ArrayList<>();
        for (int i = 1; i <= cols; i++) colNames.add(md.getColumnLabel(i));
        r.columns = colNames;

        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        while (rs.next()) {
            if (n >= maxRows) { r.truncated = true; break; }
            List<Object> row = new ArrayList<>(cols);
            for (int i = 1; i <= cols; i++) {
                row.add(safeValue(rs, i, md.getColumnType(i)));
            }
            rows.add(row);
            n++;
        }
        r.rows = rows;
    }

    /** Browse a single table/view with pagination, optional sorting and column filters (works on 11g+). */
    public QueryResult browseTable(String tableName, int page, int pageSize, String sortCol, String sortDir,
                                   Map<String, String> tableFilters, List<ColumnFilter> columnFilters) {
        String safe = sanitizeIdentifier(tableName);
        String tableFilter = tableFilters != null ? tableFilters.get(safe) : null;
        WhereClause where = buildWhere(safe, tableFilter, columnFilters);
        int start = page * pageSize;
        int end = start + pageSize;

        String orderBy = "";
        if (sortCol != null && !sortCol.trim().isEmpty()) {
            String col = sortCol.trim().toUpperCase();
            if (!schema.getColumnNames(safe).contains(col)) {
                throw new IllegalArgumentException("Unknown sort column: " + sortCol);
            }
            String dir = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
            String dataType = schema.getColumnDataType(safe, col);
            String orderExpr = smartOrderExpr("\"" + col + "\"", dataType, dir);
            orderBy = " ORDER BY " + orderExpr;
        }

        String inner = "SELECT * FROM \"" + safe + "\"" + where.sql + orderBy;
        String paged =
            "SELECT * FROM ( " +
            "  SELECT a.*, ROWNUM rnum_ FROM ( " + inner + " ) a WHERE ROWNUM <= " + end +
            ") WHERE rnum_ > " + start;

        QueryResult res = new QueryResult();
        try (Connection conn = dataSource.getConnection()) {
            res = execPrepared(conn, paged, where.binds, pageSize);
        } catch (SQLException e) {
            res.error = "Connection error: " + e.getMessage();
        }
        if (res.columns != null && res.columns.contains("RNUM_")) {
            int idx = res.columns.indexOf("RNUM_");
            res.columns.remove(idx);
            if (res.rows != null) for (List<Object> row : res.rows) row.remove(idx);
        }
        return res;
    }

    public long countTable(String tableName, Map<String, String> tableFilters, List<ColumnFilter> columnFilters) {
        String safe = sanitizeIdentifier(tableName);
        String tableFilter = tableFilters != null ? tableFilters.get(safe) : null;
        WhereClause where = buildWhere(safe, tableFilter, columnFilters);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM \"" + safe + "\"" + where.sql)) {
            bind(ps, where.binds);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            return -1L;
        }
    }

    /** A WHERE fragment plus the ordered bind values it references. */
    static final class WhereClause {
        final String sql;
        final List<Object> binds;
        WhereClause(String sql, List<Object> binds) { this.sql = sql; this.binds = binds; }
    }

    /**
     * Builds a parameterized WHERE clause combining the user's row-level table filter
     * (trusted, admin-configured) with the supplied column filters (AND-ed together).
     * Column names are validated against the schema and values are always bound, never
     * concatenated, so user-supplied filters cannot inject SQL.
     */
    WhereClause buildWhere(String safe, String tableFilter, List<ColumnFilter> columnFilters) {
        List<String> conditions = new ArrayList<>();
        List<Object> binds = new ArrayList<>();

        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            conditions.add("(" + tableFilter + ")");
        }

        if (columnFilters != null && !columnFilters.isEmpty()) {
            Set<String> validCols = schema.getColumnNames(safe);
            for (ColumnFilter f : columnFilters) {
                if (f == null || f.column == null || f.column.trim().isEmpty()) continue;
                String col = f.column.trim().toUpperCase();
                if (!validCols.contains(col)) {
                    throw new IllegalArgumentException("Unknown filter column: " + f.column);
                }
                String q = "\"" + col + "\"";
                String op = f.operator == null ? "EQ" : f.operator.trim().toUpperCase();
                String v = f.value == null ? "" : f.value;
                switch (op) {
                    case "EQ":       conditions.add(q + " = ?");           binds.add(v); break;
                    case "NEQ":      conditions.add(q + " <> ?");          binds.add(v); break;
                    case "GT":       conditions.add(q + " > ?");           binds.add(v); break;
                    case "GTE":      conditions.add(q + " >= ?");          binds.add(v); break;
                    case "LT":       conditions.add(q + " < ?");           binds.add(v); break;
                    case "LTE":      conditions.add(q + " <= ?");          binds.add(v); break;
                    case "CONTAINS": conditions.add("UPPER(" + q + ") LIKE ?"); binds.add("%" + v.toUpperCase() + "%"); break;
                    case "STARTS":   conditions.add("UPPER(" + q + ") LIKE ?"); binds.add(v.toUpperCase() + "%"); break;
                    case "ENDS":     conditions.add("UPPER(" + q + ") LIKE ?"); binds.add("%" + v.toUpperCase()); break;
                    case "NULL":     conditions.add(q + " IS NULL"); break;
                    case "NOTNULL":  conditions.add(q + " IS NOT NULL"); break;
                    default: throw new IllegalArgumentException("Unknown filter operator: " + op);
                }
            }
        }

        String sql = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new WhereClause(sql, binds);
    }

    private static void bind(PreparedStatement ps, List<Object> binds) throws SQLException {
        for (int i = 0; i < binds.size(); i++) ps.setObject(i + 1, binds.get(i));
    }

    /** Run a parameterized read query and fill a QueryResult (mirrors execOne for binds). */
    private QueryResult execPrepared(Connection conn, String sql, List<Object> binds, int maxRows) {
        QueryResult r = new QueryResult();
        r.statement = sql.length() > 500 ? sql.substring(0, 500) + " ..." : sql;
        long start = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (props.getQueryTimeoutSeconds() > 0) ps.setQueryTimeout(props.getQueryTimeoutSeconds());
            ps.setMaxRows(maxRows + 1);
            bind(ps, binds);
            try (ResultSet rs = ps.executeQuery()) {
                fillResultSet(r, rs, maxRows);
                r.resultSet = true;
            }
        } catch (SQLException e) {
            log.warn("SQL error for stmt [{}]: {}", r.statement, e.getMessage());
            r.error = "Query execution failed.";
        }
        r.elapsedMs = System.currentTimeMillis() - start;
        return r;
    }

    public Map<String, Object> getTableInsights(String tableName, Map<String, String> tableFilters) {
        String safe = sanitizeIdentifier(tableName);
        String tableFilter = tableFilters != null ? tableFilters.get(safe) : null;
        String filterClause = (tableFilter != null && !tableFilter.trim().isEmpty()) ? " WHERE (" + tableFilter + ")" : "";

        Map<String, Object> insights = new HashMap<>();
        String owner = schema.resolveSchema();

        // Get columns from all_tab_columns (uses schema cache, no JDBC metadata)
        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT column_name, data_type FROM all_tab_columns WHERE owner = ? AND table_name = ? ORDER BY column_id")) {
            ps.setString(1, owner);
            ps.setString(2, safe);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> col = new HashMap<>();
                    col.put("name", rs.getString("column_name"));
                    col.put("type", rs.getString("data_type"));
                    columns.add(col);
                }
            }
        } catch (SQLException e) {
            insights.put("error", e.getMessage());
            return insights;
        }

        // Build a single UNION ALL query for basic stats across all columns
        List<Map<String, Object>> colStats = new ArrayList<>();
        if (!columns.isEmpty()) {
            StringBuilder unionSql = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) unionSql.append(" UNION ALL ");
                String colName = "\"" + columns.get(i).get("name") + "\"";
                unionSql.append("SELECT COUNT(*), COUNT(").append(colName)
                        .append("), COUNT(DISTINCT ").append(colName)
                        .append(") FROM \"").append(safe).append("\"").append(filterClause);
            }
            try (Connection conn = dataSource.getConnection();
                 Statement st = timedStatement(conn);
                 ResultSet rs = st.executeQuery(unionSql.toString())) {
                int idx = 0;
                while (rs.next() && idx < columns.size()) {
                    Map<String, String> col = columns.get(idx);
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("column", col.get("name"));
                    stats.put("type", col.get("type"));
                    long total = rs.getLong(1);
                    long nonNull = rs.getLong(2);
                    stats.put("totalRows", total);
                    stats.put("nullCount", total - nonNull);
                    stats.put("uniqueCount", rs.getLong(3));
                    colStats.add(stats);
                    idx++;
                }
            } catch (SQLException e) {
                insights.put("error", e.getMessage());
                return insights;
            }

            // Numeric and date extra stats (per typed column)
            try (Connection conn = dataSource.getConnection()) {
                for (int i = 0; i < columns.size(); i++) {
                    Map<String, String> col = columns.get(i);
                    Map<String, Object> stats = colStats.get(i);
                    String type = col.get("type");
                    String colName = "\"" + col.get("name") + "\"";

                    if (type.contains("DATE") || type.contains("TIMESTAMP")) {
                        Map<String, Long> timeStats = new LinkedHashMap<>();
                        String[] periods = {"1", "6", "12", "24"};
                        for (String p : periods) {
                            String timeSql = "SELECT COUNT(*) FROM \"" + safe + "\"" +
                                (filterClause.isEmpty() ? " WHERE " : filterClause + " AND ") +
                                colName + " >= ADD_MONTHS(SYSDATE, -" + p + ")";
                            try (Statement st = timedStatement(conn);
                                 ResultSet rs = st.executeQuery(timeSql)) {
                                if (rs.next()) timeStats.put("last_" + p + "_months", rs.getLong(1));
                            }
                        }
                        stats.put("timeAnalysis", timeStats);
                    }

                    if (type.contains("NUMBER") || type.contains("FLOAT") || type.contains("DECIMAL")) {
                        try (Statement st = timedStatement(conn);
                             ResultSet rs = st.executeQuery("SELECT MIN(" + colName + "), MAX(" + colName + "), AVG(" + colName + ") FROM \"" + safe + "\"" + filterClause)) {
                            if (rs.next()) {
                                stats.put("min", rs.getObject(1));
                                stats.put("max", rs.getObject(2));
                                stats.put("avg", rs.getObject(3));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                insights.put("partialError", e.getMessage());
            }
        }

        insights.put("columnStats", colStats);
        return insights;
    }

    /**
     * For each PK column of a table, determine whether it is auto-generated
     * (identity column or a BEFORE INSERT trigger exists) and, if not, what the
     * next suggested value is (MAX + 1).
     *
     * Returns a map: { "autoGenerated": true|false, "nextValue": <Long|null> }
     * per PK column name.
     */
    public Map<String, Object> pkNextInfo(String tableName) {
        String safe = sanitizeIdentifier(tableName);
        String owner = schema.resolveSchema();
        List<String> pks = schema.getPkColumns(safe);

        Map<String, Object> result = new LinkedHashMap<>();
        if (pks.isEmpty()) return result;

        try (Connection conn = dataSource.getConnection()) {
            for (String pkCol : pks) {
                Map<String, Object> info = new LinkedHashMap<>();

                // 1. Check for Oracle identity column (12c+)
                boolean isIdentity = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM all_tab_identity_cols WHERE owner=? AND table_name=? AND column_name=?")) {
                    ps.setString(1, owner);
                    ps.setString(2, safe);
                    ps.setString(3, pkCol);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) isIdentity = true;
                    }
                } catch (SQLException ignore) { /* table may not exist in older Oracle */ }

                // 2. Check for BEFORE INSERT trigger on the table
                boolean hasTrigger = false;
                if (!isIdentity) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM all_triggers WHERE owner=? AND table_name=? AND triggering_event LIKE '%INSERT%' AND trigger_type LIKE '%BEFORE%' AND status='ENABLED'")) {
                        ps.setString(1, owner);
                        ps.setString(2, safe);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) hasTrigger = true;
                        }
                    }
                }

                boolean autoGenerated = isIdentity || hasTrigger;
                info.put("autoGenerated", autoGenerated);

                // 3. For user-assigned PKs, fetch MAX + 1
                if (!autoGenerated) {
                    try (Statement st = timedStatement(conn);
                         ResultSet rs = st.executeQuery(
                                 "SELECT NVL(MAX(\"" + pkCol + "\"), 0) + 1 FROM \"" + safe + "\"")) {
                        info.put("nextValue", rs.next() ? rs.getLong(1) : 1L);
                    } catch (SQLException e) {
                        info.put("nextValue", null);
                    }
                }

                result.put(pkCol, info);
            }
        } catch (SQLException e) {
            log.warn("pkNextInfo failed for {}: {}", safe, e.getMessage());
        }
        return result;
    }

    // ---- helpers ----

    private static Object safeValue(ResultSet rs, int i, int sqlType) throws SQLException {
        switch (sqlType) {
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                Object b = rs.getObject(i);
                return b == null ? null : "[BLOB]";
            case Types.CLOB:
            case Types.NCLOB:
                Clob clob = rs.getClob(i);
                if (clob == null) return null;
                long len = clob.length();
                String s = clob.getSubString(1, (int) Math.min(len, 2000));
                return len > 2000 ? s + " ...[truncated]" : s;
            case Types.TIMESTAMP:
            case Types.DATE:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                Object ts = rs.getObject(i);
                return ts == null ? null : ts.toString();
            default:
                return rs.getObject(i);
        }
    }

    private boolean isReadOnlyStatement(String sql) {
        String head = sql.trim().toUpperCase();
        return head.startsWith("SELECT") || head.startsWith("WITH")
                || head.startsWith("EXPLAIN") || head.startsWith("DESC");
    }

    /**
     * Returns an ORDER BY expression that sorts correctly for the column's data type.
     * - NUMBER/FLOAT/INTEGER → sort as-is (already numeric in Oracle)
     * - DATE/TIMESTAMP       → sort as-is (native temporal comparison)
     * - CHAR/VARCHAR2 that looks purely numeric → TO_NUMBER(col) with NULLS LAST fallback
     * - Everything else      → plain column reference (lexicographic)
     */
    private static String smartOrderExpr(String quotedCol, String dataType, String dir) {
        if (dataType == null) return quotedCol + " " + dir;
        if (dataType.contains("NUMBER") || dataType.contains("FLOAT")
                || dataType.contains("INTEGER") || dataType.contains("DECIMAL")
                || dataType.contains("INT")) {
            return quotedCol + " " + dir;
        }
        if (dataType.contains("DATE") || dataType.contains("TIMESTAMP")) {
            return quotedCol + " " + dir;
        }
        // For text columns, attempt numeric sort with dir applied to each part.
        // Non-numeric values fall after numeric ones then sort as text (case-insensitive).
        if (dataType.contains("CHAR") || dataType.contains("VARCHAR")) {
            return "CASE WHEN REGEXP_LIKE(" + quotedCol + ", '^[0-9]+(\\.[0-9]+)?$') " +
                   "THEN TO_NUMBER(" + quotedCol + ") END " + dir + " NULLS LAST, " +
                   "UPPER(" + quotedCol + ") " + dir;
        }
        return quotedCol + " " + dir;
    }

    /** Allow only safe identifier characters to avoid breaking the quoted name. */
    private static String sanitizeIdentifier(String name) {
        String n = name.toUpperCase();
        if (!n.matches("[A-Z0-9_$#]+")) {
            throw new IllegalArgumentException("Invalid object name: " + name);
        }
        return n;
    }

    /**
     * Creates a Statement with the configured query timeout already applied, so the
     * insights/PK-info paths can't run unbounded against a large table. Mirrors the
     * timeout the run/browse paths set explicitly.
     */
    private Statement timedStatement(Connection conn) throws SQLException {
        Statement st = conn.createStatement();
        if (props.getQueryTimeoutSeconds() > 0) {
            st.setQueryTimeout(props.getQueryTimeoutSeconds());
        }
        return st;
    }
}
