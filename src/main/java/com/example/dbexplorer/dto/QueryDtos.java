package com.example.dbexplorer.dto;

import java.util.List;

public class QueryDtos {

    public static class QueryRequest {
        public String sql;
        public Integer maxRows; // optional override
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
