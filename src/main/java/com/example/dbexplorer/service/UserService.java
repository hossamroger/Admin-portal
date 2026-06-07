package com.example.dbexplorer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbc;

    public UserService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> listUsers() {
        return jdbc.queryForList("SELECT USERNAME, ROLE, ENABLED FROM DBX_USERS ORDER BY USERNAME");
    }

    public Map<String,Object> getUserDetails(String username) {
        Map<String,Object> user;
        try {
            user = jdbc.queryForMap(
                "SELECT USERNAME, ROLE, ENABLED FROM DBX_USERS WHERE USERNAME = ?", username);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        List<String> privs = jdbc.queryForList(
            "SELECT PRIVILEGE FROM DBX_USER_PRIVILEGES WHERE USERNAME = ?", String.class, username);
        List<String> tables = jdbc.queryForList(
            "SELECT TABLE_NAME FROM DBX_USER_ALLOWED_TABLES WHERE USERNAME = ?", String.class, username);
        List<Map<String,Object>> filters = jdbc.queryForList(
            "SELECT TABLE_NAME, FILTER_CONDITION FROM DBX_USER_TABLE_FILTERS WHERE USERNAME = ?", username);
        user.put("privileges", privs);
        user.put("allowedTables", tables);
        user.put("filters", filters);
        return user;
    }

    @Transactional
    public void saveUser(String username, String password, String role,
                         List<String> privileges, List<String> allowedTables,
                         List<Map<String,String>> filters) {
        if (username == null || username.trim().isEmpty())
            throw new IllegalArgumentException("Username is required.");

        if ("admin".equalsIgnoreCase(username) && !"ADMIN".equalsIgnoreCase(role))
            throw new IllegalArgumentException("Cannot demote the primary admin account.");

        java.util.Set<String> validPrivileges = new java.util.HashSet<>(java.util.Arrays.asList(
            "SELECT", "INSERT", "UPDATE", "DELETE", "EXECUTE_SQL"));
        if (privileges != null) {
            for (String p : privileges) {
                if (!validPrivileges.contains(p))
                    throw new IllegalArgumentException("Invalid privilege: " + p);
            }
        }

        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM DBX_USERS WHERE USERNAME = ?", Integer.class, username);

        if (count == 0) {
            if (password == null || password.isEmpty())
                throw new IllegalArgumentException("Password is required for new users.");
            String pwd = encodePassword(password);
            jdbc.update("INSERT INTO DBX_USERS (USERNAME, PASSWORD, ROLE, ENABLED) VALUES (?, ?, ?, 'Y')",
                username, pwd, role);
        } else {
            jdbc.update("UPDATE DBX_USERS SET ROLE = ? WHERE USERNAME = ?", role, username);
            if (password != null && !password.isEmpty()) {
                jdbc.update("UPDATE DBX_USERS SET PASSWORD = ? WHERE USERNAME = ?",
                    encodePassword(password), username);
            }
        }

        jdbc.update("DELETE FROM DBX_USER_PRIVILEGES WHERE USERNAME = ?", username);
        if (privileges != null) {
            for (String p : privileges)
                jdbc.update("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES (?, ?)", username, p);
        }

        jdbc.update("DELETE FROM DBX_USER_ALLOWED_TABLES WHERE USERNAME = ?", username);
        if (allowedTables != null) {
            for (String t : allowedTables)
                jdbc.update("INSERT INTO DBX_USER_ALLOWED_TABLES (USERNAME, TABLE_NAME) VALUES (?, ?)",
                    username, t.toUpperCase());
        }

        jdbc.update("DELETE FROM DBX_USER_TABLE_FILTERS WHERE USERNAME = ?", username);
        if (filters != null) {
            for (Map<String,String> f : filters) {
                String tableName = f.get("TABLE_NAME");
                if (tableName == null || tableName.trim().isEmpty()) continue;
                jdbc.update(
                    "INSERT INTO DBX_USER_TABLE_FILTERS (USERNAME, TABLE_NAME, FILTER_CONDITION) VALUES (?, ?, ?)",
                    username, tableName.toUpperCase(), f.get("FILTER_CONDITION"));
            }
        }
        log.info("User '{}' saved by admin", username);
    }

    @Transactional
    public void deleteUser(String username) {
        if ("admin".equalsIgnoreCase(username))
            throw new IllegalArgumentException("Cannot delete the primary admin account.");
        jdbc.update("DELETE FROM DBX_USERS WHERE USERNAME = ?", username);
        log.info("User '{}' deleted", username);
    }

    private static String encodePassword(String raw) {
        if (raw.startsWith("{bcrypt}") || raw.startsWith("$2a$") || raw.startsWith("{noop}"))
            return raw; // already encoded — preserve as-is
        return "{bcrypt}" + BCRYPT.encode(raw);
    }
}
