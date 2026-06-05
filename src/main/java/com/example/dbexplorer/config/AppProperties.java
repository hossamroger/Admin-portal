package com.example.dbexplorer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "dbexplorer")
public class AppProperties {

    private String defaultSchema = "";
    private boolean readOnly = false;
    private int maxRows = 1000;
    private int queryTimeoutSeconds = 60;
    private Auth auth = new Auth();
    private Audit audit = new Audit();

    public String getDefaultSchema() { return defaultSchema; }
    public void setDefaultSchema(String defaultSchema) { this.defaultSchema = defaultSchema; }

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }

    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }

    /** Asynchronous audit-logging settings. */
    public static class Audit {
        private boolean enabled = true;
        /** Try to create the audit table on startup if it doesn't exist. */
        private boolean autoCreateTable = true;
        /** Log read-only actions (GET) too, not just data changes. */
        private boolean logReads = true;
        /** Max events buffered in memory; extra events are dropped (never block a request). */
        private int queueCapacity = 10000;
        /** Max rows inserted per batch by the background writer. */
        private int batchSize = 500;
        /** Audit table name. */
        private String table = "DBX_AUDIT_LOG";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAutoCreateTable() { return autoCreateTable; }
        public void setAutoCreateTable(boolean v) { this.autoCreateTable = v; }
        public boolean isLogReads() { return logReads; }
        public void setLogReads(boolean v) { this.logReads = v; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int v) { this.queueCapacity = v; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int v) { this.batchSize = v; }
        public String getTable() { return table; }
        public void setTable(String v) { this.table = v; }
    }

    /** Authentication / authorization settings. */
    public static class Auth {
        private boolean enabled = true;
        private List<User> users = new ArrayList<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<User> getUsers() { return users; }
        public void setUsers(List<User> users) { this.users = users; }
    }

    /** A single application user. */
    public static class User {
        private String username;
        /** "{noop}plain" for demo, or a BCrypt hash (starts with $2a$/$2b$ or "{bcrypt}..."). */
        private String password;
        private String role = "USER";
        /** SELECT, INSERT, UPDATE, DELETE, EXECUTE_SQL */
        private List<String> privileges = new ArrayList<>();
        /** Whitelist of table/view names this user may see. Empty = all. */
        private List<String> allowedTables = new ArrayList<>();
        private java.util.Map<String, String> tableFilters = new java.util.HashMap<>();

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public List<String> getPrivileges() { return privileges; }
        public void setPrivileges(List<String> privileges) { this.privileges = privileges; }
        public List<String> getAllowedTables() { return allowedTables; }
        public void setAllowedTables(List<String> allowedTables) { this.allowedTables = allowedTables; }

        public java.util.Map<String, String> getTableFilters() { return tableFilters; }
        public void setTableFilters(java.util.Map<String, String> tableFilters) { this.tableFilters = tableFilters; }
    }
}
