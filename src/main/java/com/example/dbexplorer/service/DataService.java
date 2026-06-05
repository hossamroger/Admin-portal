package com.example.dbexplorer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Safe, parameterized data modification. Column/table identifiers are validated
 * against the live schema metadata (never interpolated from raw user input),
 * and all values are bound as JDBC parameters.
 */
@Service
public class DataService {

    private final JdbcTemplate jdbc;
    private final SchemaService schema;
    private final AuthService auth;

    public DataService(JdbcTemplate jdbc, SchemaService schema, AuthService auth) {
        this.jdbc = jdbc;
        this.schema = schema;
        this.auth = auth;
    }

    public int insert(String table, Map<String, Object> values, javax.servlet.http.HttpServletRequest req) {
        String t = ident(table);
        Set<String> valid = schema.getColumnNames(t);
        List<String> cols = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String col = requireColumn(e.getKey(), valid);
            cols.add(col);
            params.add(normalize(e.getValue()));
        }
        if (cols.isEmpty()) throw new IllegalArgumentException("No values provided.");

        String colList = String.join(", ", quote(cols));
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO \"" + t + "\" (" + colList + ") VALUES (" + placeholders + ")";
        // Apply row-level filter for INSERT: ensure the inserted row matches the user's filter
        String tableFilter = auth.getTableFilters(auth.effectiveUser(req)).get(t);
        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            // For INSERT, we need to verify if the inserted data would satisfy the filter.
            // A simple way is to check if the filter conditions are met by the provided values.
            // This is a basic check and might need more sophisticated parsing for complex filters.
            // For now, we\'ll just ensure that if a column in the filter is present in values, it matches.
            // If not, we\'ll throw an exception.
            // More robust RLS for INSERT might involve dynamic SQL generation or policy functions.
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String colName = entry.getKey();
                Object colValue = entry.getValue();
                // This is a very basic check. A real RLS would parse the filter.
                // For example, if filter is "DEPT_ID = 10" and values has DEPT_ID = 20, reject.
                if (tableFilter.toUpperCase().contains(colName.toUpperCase() + " =") && !tableFilter.toUpperCase().contains(colName.toUpperCase() + " = " + formatValueForSql(colValue).toUpperCase())) {
                    throw new SecurityException("Inserted data does not match your row-level filter for table " + table + ".");
                }
            }
        }
        return jdbc.update(sql, params.toArray());
    }

    public int update(String table, Map<String, Object> key, Map<String, Object> values, javax.servlet.http.HttpServletRequest req) {
        String t = ident(table);
        Set<String> valid = schema.getColumnNames(t);
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("Primary key is required to update a row.");
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("No changed values provided.");

        List<String> setCols = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            setCols.add(requireColumn(e.getKey(), valid));
            params.add(normalize(e.getValue()));
        }
        List<String> whereCols = new ArrayList<>();
        for (Map.Entry<String, Object> e : key.entrySet()) {
            whereCols.add(requireColumn(e.getKey(), valid));
            params.add(normalize(e.getValue()));
        }
        String setClause = joinAssign(setCols);
        String whereClause = joinAssign(whereCols).replace(", ", " AND ");
        // Apply row-level filter for UPDATE
        String tableFilter = auth.getTableFilters(auth.effectiveUser(req)).get(t);
        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            whereClause += " AND (" + tableFilter + ")";
        }
        String sql = "UPDATE \"" + t + "\" SET " + setClause + " WHERE " + whereClause;
        return jdbc.update(sql, params.toArray());
    }

    public int delete(String table, Map<String, Object> key, javax.servlet.http.HttpServletRequest req) {
        String t = ident(table);
        Set<String> valid = schema.getColumnNames(t);
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("Primary key is required to delete a row.");
        List<String> whereCols = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : key.entrySet()) {
            whereCols.add(requireColumn(e.getKey(), valid));
            params.add(normalize(e.getValue()));
        }
        String whereClause = joinAssign(whereCols).replace(", ", " AND ");
        // Apply row-level filter for DELETE
        String tableFilter = auth.getTableFilters(auth.effectiveUser(req)).get(t);
        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            whereClause += " AND (" + tableFilter + ")";
        }
        String sql = "DELETE FROM \"" + t + "\" WHERE " + whereClause;
        return jdbc.update(sql, params.toArray());
    }

    // ---- helpers ----

    private static String joinAssign(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(cols.get(i)).append("\" = ?");
        }
        return sb.toString();
    }

    private static List<String> quote(List<String> cols) {
        List<String> out = new ArrayList<>(cols.size());
        for (String c : cols) out.add("\"" + c + "\"");
        return out;
    }

    private static String requireColumn(String raw, Set<String> valid) {
        if (raw == null) throw new IllegalArgumentException("Null column name.");
        String c = raw.trim().toUpperCase();
        if (!valid.contains(c)) throw new IllegalArgumentException("Unknown column: " + raw);
        return c;
    }

    private static String ident(String name) {
        if (name == null || !name.toUpperCase().matches("[A-Z0-9_$#]+"))
            throw new IllegalArgumentException("Invalid table name: " + name);
        return name.toUpperCase();
    }

    /** Treat empty strings as SQL NULL; pass everything else straight to JDBC. */
    private static Object normalize(Object v) {
        if (v == null) return null;
        if (v instanceof String && ((String) v).isEmpty()) return null;
        return v;
    }

    // Helper to format values for SQL comparison (basic, needs improvement for dates/complex types)
    private static String formatValueForSql(Object value) {
        if (value == null) return "NULL";
        if (value instanceof String) return "\''" + ((String) value).replace("\''", "\''\''") + "\''"; // Use single quotes for SQL strings
        return value.toString();
    }
}
