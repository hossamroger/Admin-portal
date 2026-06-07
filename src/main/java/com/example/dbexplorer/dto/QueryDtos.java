package com.example.dbexplorer.dto;

import java.util.List;

public class QueryDtos {

    public static class QueryRequest {
        public String sql;
        public Integer maxRows; // optional override
    }

    /**
     * A single column-level filter applied when browsing a table.
     * Several filters are combined with AND (single or multi-column filtering).
     */
    public static class ColumnFilter {
        public String column;    // column name (validated against the table's schema)
        public String operator;  // EQ, NEQ, GT, GTE, LT, LTE, CONTAINS, STARTS, ENDS, NULL, NOTNULL
        public String value;     // bound as a parameter; ignored for NULL/NOTNULL
    }

    /** Result of a single statement execution. */
    public static class QueryResult {
        public boolean resultSet;       // true if it returned rows (SELECT)
        public List<String> columns;    // column names (when resultSet)
        public List<List<Object>> rows; // row data (when resultSet)
        public Integer updateCount;     // affected rows (when DML/DDL)
        public boolean truncated;       // true if rows were capped at maxRows
        public long elapsedMs;
        public String statement;        // the executed statement (trimmed)
        public String error;            // non-null if this statement failed
    }
}
