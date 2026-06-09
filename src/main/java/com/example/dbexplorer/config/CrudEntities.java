package com.example.dbexplorer.config;

import java.util.*;

/**
 * Whitelist of tables exposed through the generic CRUD API (/api/crud/{entity}).
 * Adding a new admin screen = one entry here + one config object in the frontend.
 */
public class CrudEntities {

    public static class LookupDef {
        public final String table, idCol, labelCol, orderBy;
        public LookupDef(String table, String idCol, String labelCol, String orderBy) {
            this.table = table; this.idCol = idCol; this.labelCol = labelCol; this.orderBy = orderBy;
        }
    }

    public static class CrudEntity {
        public final String name;                 // URL segment, e.g. "process-status"
        public final String table;                // Oracle table name
        public final String pk;                   // PK column (auto-filled with MAX+1 on insert)
        public String orderCol;                   // optional: auto MAX+1 when null on insert
        public String createdAtCol, updatedAtCol; // optional: auto SYSTIMESTAMP
        public List<String> searchCols = Collections.emptyList();
        public String defaultOrderBy;             // ORDER BY clause for the list
        public Set<String> ops = new HashSet<>(Arrays.asList("SELECT", "INSERT", "UPDATE"));
        public Map<String, LookupDef> lookups = new HashMap<>();

        public CrudEntity(String name, String table, String pk) {
            this.name = name; this.table = table; this.pk = pk;
            this.defaultOrderBy = pk;
        }
    }

    private static final Map<String, CrudEntity> REGISTRY = new LinkedHashMap<>();

    static {
        CrudEntity ps = new CrudEntity("process-status", "BPM_PROCESS_STATUS", "ID");
        ps.searchCols = Arrays.asList("PROCESS_NAME", "PROCESS_CODE");
        ps.lookups.put("msgs", new LookupDef("BPM_PROCESS_STATUS_MSG", "ID", "MESSAGE_EN", "ID"));
        REGISTRY.put(ps.name, ps);

        CrudEntity hb = new CrudEntity("home-banner", "DS_HOME_BANNER_CONFIG", "ID");
        hb.searchCols = Arrays.asList("URL", "URL_SM", "PLATFORM");
        hb.orderCol = "BANNER_ORDER";
        hb.createdAtCol = "CREATED_AT";
        hb.updatedAtCol = "UPDATED_AT";
        hb.defaultOrderBy = "BANNER_ORDER NULLS LAST, ID";
        REGISTRY.put(hb.name, hb);
    }

    public static CrudEntity get(String name) {
        return REGISTRY.get(name);
    }

    private CrudEntities() {}
}
