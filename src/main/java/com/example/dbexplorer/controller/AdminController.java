package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.service.AuthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    private void requireAdmin(HttpServletRequest req) {
        User u = auth.effectiveUser(req);
        if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) {
            throw new SecurityException("Admin access required.");
        }
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers(HttpServletRequest req) {
        requireAdmin(req);
        return jdbc.queryForList("SELECT USERNAME, ROLE, ENABLED FROM DBX_USERS ORDER BY USERNAME");
    }

    @GetMapping("/user/{username}")
    public Map<String, Object> getUserDetails(@PathVariable String username, HttpServletRequest req) {
        requireAdmin(req);
        Map<String, Object> user = jdbc.queryForMap("SELECT USERNAME, ROLE, ENABLED FROM DBX_USERS WHERE USERNAME = ?", username);
        List<String> privs = jdbc.queryForList("SELECT PRIVILEGE FROM DBX_USER_PRIVILEGES WHERE USERNAME = ?", String.class, username);
        List<String> tables = jdbc.queryForList("SELECT TABLE_NAME FROM DBX_USER_ALLOWED_TABLES WHERE USERNAME = ?", String.class, username);
        List<Map<String, Object>> filters = jdbc.queryForList("SELECT TABLE_NAME, FILTER_CONDITION FROM DBX_USER_TABLE_FILTERS WHERE USERNAME = ?", username);
        
        Map<String, Object> res = new HashMap<>(user);
        res.put("privileges", privs);
        res.put("allowedTables", tables);
        res.put("filters", filters);
        return res;
    }

    @PostMapping("/user")
    public void saveUser(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        requireAdmin(req);
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String role = (String) body.get("role");
        
        // Upsert User
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM DBX_USERS WHERE USERNAME = ?", Integer.class, username);
        if (count == 0) {
            String pwd = (password != null && !password.isEmpty()) ? password : "{noop}password123";
            if (!pwd.startsWith("{noop}") && !pwd.startsWith("{bcrypt}") && !pwd.startsWith("$2a$")) pwd = "{noop}" + pwd;
            jdbc.update("INSERT INTO DBX_USERS (USERNAME, PASSWORD, ROLE) VALUES (?, ?, ?)", username, pwd, role);
        } else {
            jdbc.update("UPDATE DBX_USERS SET ROLE = ? WHERE USERNAME = ?", role, username);
            if (password != null && !password.isEmpty()) {
                String pwd = password;
                if (!pwd.startsWith("{noop}") && !pwd.startsWith("{bcrypt}") && !pwd.startsWith("$2a$")) pwd = "{noop}" + pwd;
                jdbc.update("UPDATE DBX_USERS SET PASSWORD = ? WHERE USERNAME = ?", pwd, username);
            }
        }

        // Update Privileges
        jdbc.update("DELETE FROM DBX_USER_PRIVILEGES WHERE USERNAME = ?", username);
        List<String> privs = (List<String>) body.get("privileges");
        if (privs != null) {
            for (String p : privs) jdbc.update("INSERT INTO DBX_USER_PRIVILEGES (USERNAME, PRIVILEGE) VALUES (?, ?)", username, p);
        }

        // Update Allowed Tables
        jdbc.update("DELETE FROM DBX_USER_ALLOWED_TABLES WHERE USERNAME = ?", username);
        List<String> tables = (List<String>) body.get("allowedTables");
        if (tables != null) {
            for (String t : tables) jdbc.update("INSERT INTO DBX_USER_ALLOWED_TABLES (USERNAME, TABLE_NAME) VALUES (?, ?)", username, t.toUpperCase());
        }

        // Update Filters
        jdbc.update("DELETE FROM DBX_USER_TABLE_FILTERS WHERE USERNAME = ?", username);
        List<Map<String, String>> filters = (List<Map<String, String>>) body.get("filters");
        if (filters != null) {
            for (Map<String, String> f : filters) {
                jdbc.update("INSERT INTO DBX_USER_TABLE_FILTERS (USERNAME, TABLE_NAME, FILTER_CONDITION) VALUES (?, ?, ?)", 
                    username, f.get("TABLE_NAME").toUpperCase(), f.get("FILTER_CONDITION"));
            }
        }
    }

    @DeleteMapping("/user/{username}")
    public void deleteUser(@PathVariable String username, HttpServletRequest req) {
        requireAdmin(req);
        if ("admin".equalsIgnoreCase(username)) throw new IllegalArgumentException("Cannot delete primary admin.");
        jdbc.update("DELETE FROM DBX_USERS WHERE USERNAME = ?", username);
    }
}
