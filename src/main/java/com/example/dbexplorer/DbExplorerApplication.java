package com.example.dbexplorer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DbExplorerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbExplorerApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.CommandLineRunner initConfigTables(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return args -> {
            try {
                // Check if DBX_USERS exists
                jdbc.queryForObject("SELECT COUNT(*) FROM DBX_USERS WHERE ROWNUM = 1", Integer.class);
            } catch (Exception e) {
                System.out.println("Config tables not found. Creating them...");
                // Create DBX_USERS
                jdbc.execute("CREATE TABLE DBX_USERS (USERNAME VARCHAR2(50) PRIMARY KEY, PASSWORD VARCHAR2(255) NOT NULL, ROLE VARCHAR2(20) DEFAULT 'USER', ENABLED CHAR(1) DEFAULT 'Y')");
                // Create DBX_USER_PRIVILEGES
                jdbc.execute("CREATE TABLE DBX_USER_PRIVILEGES (USERNAME VARCHAR2(50), PRIVILEGE VARCHAR2(50), PRIMARY KEY (USERNAME, PRIVILEGE))");
                // Create DBX_USER_ALLOWED_TABLES
                jdbc.execute("CREATE TABLE DBX_USER_ALLOWED_TABLES (USERNAME VARCHAR2(50), TABLE_NAME VARCHAR2(128), PRIMARY KEY (USERNAME, TABLE_NAME))");
                // Create DBX_USER_TABLE_FILTERS
                jdbc.execute("CREATE TABLE DBX_USER_TABLE_FILTERS (USERNAME VARCHAR2(50), TABLE_NAME VARCHAR2(128), FILTER_CONDITION VARCHAR2(4000), PRIMARY KEY (USERNAME, TABLE_NAME))");
                
                // Insert Default Admin
                jdbc.execute("INSERT INTO DBX_USERS (USERNAME, PASSWORD, ROLE) VALUES ('admin', '{noop}admin123', 'ADMIN')");
                jdbc.execute("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES ('admin', 'SELECT')");
                jdbc.execute("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES ('admin', 'INSERT')");
                jdbc.execute("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES ('admin', 'UPDATE')");
                jdbc.execute("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES ('admin', 'DELETE')");
                jdbc.execute("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES ('admin', 'EXECUTE_SQL')");
                System.out.println("Config tables created with default admin/admin123.");
            }
        };
    }
}
