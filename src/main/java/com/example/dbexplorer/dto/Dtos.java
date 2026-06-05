package com.example.dbexplorer.dto;

import java.util.List;

/** All metadata DTOs grouped in one file for brevity. */
public class Dtos {

    public static class DbObject {
        public String name;
        public String type;   // TABLE, VIEW, PROCEDURE, FUNCTION, PACKAGE, SEQUENCE, TRIGGER
        public DbObject() {}
        public DbObject(String name, String type) { this.name = name; this.type = type; }
    }

    public static class ColumnInfo {
        public String name;
        public String dataType;
        public Integer dataLength;
        public Integer dataPrecision;
        public Integer dataScale;
        public boolean nullable;
        public String defaultValue;
        public boolean primaryKey;
        public String comments;
    }

    public static class ConstraintInfo {
        public String name;
        public String type;          // P (PK), R (FK), U (Unique), C (Check)
        public String columns;       // comma-separated
        public String refTable;      // for FK
        public String refColumns;    // for FK
        public String searchCondition; // for check
    }

    public static class IndexInfo {
        public String name;
        public boolean unique;
        public String columns;       // comma-separated
    }

    public static class TableDetail {
        public String owner;
        public String name;
        public String type;          // TABLE or VIEW
        public String comments;
        public List<ColumnInfo> columns;
        public List<ConstraintInfo> constraints;
        public List<IndexInfo> indexes;
        public Long rowCount;        // approximate / actual count, may be null
    }
}
