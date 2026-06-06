package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.config.AppProperties.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    public static final String SESSION_USER = "DBX_USER";
    private static final String SESSION_USER_OBJ = "DBX_USER_OBJ";

    private final AppProperties props;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public AuthService(AppProperties props, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.props = props;
        this.jdbc = jdbc;
    }

    public boolean isEnabled() {
        return props.getAuth().isEnabled();
    }

    /** Verify credentials. Returns the user on success. */
    public Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        User u = loadUserFromDb(username.trim().toLowerCase());
        if (u == null) return Optional.empty();
        return passwordMatches(password, u.getPassword()) ? Optional.of(u) : Optional.empty();
    }

    /** Cache the full User object in the HTTP session. */
    public void cacheUser(HttpServletRequest req, User u) {
        req.getSession(false).setAttribute(SESSION_USER_OBJ, u);
    }

    private User loadUserFromDb(String username) {
        try {
            return jdbc.queryForObject("SELECT * FROM DBX_USERS WHERE LOWER(USERNAME) = ?", (rs, rowNum) -> {
                User u = new User();
                u.setUsername(rs.getString("USERNAME"));
                u.setPassword(rs.getString("PASSWORD"));
                u.setRole(rs.getString("ROLE"));

                // Load Privileges
                u.setPrivileges(jdbc.queryForList("SELECT PRIVILEGE FROM DBX_USER_PRIVILEGES WHERE LOWER(USERNAME) = ?", String.class, username));

                // Load Allowed Tables
                u.setAllowedTables(jdbc.queryForList("SELECT TABLE_NAME FROM DBX_USER_ALLOWED_TABLES WHERE LOWER(USERNAME) = ?", String.class, username));

                // Load Table Filters
                Map<String, String> filters = new HashMap<>();
                jdbc.query("SELECT TABLE_NAME, FILTER_CONDITION FROM DBX_USER_TABLE_FILTERS WHERE LOWER(USERNAME) = ?", rs2 -> {
                    filters.put(rs2.getString("TABLE_NAME").toUpperCase(), rs2.getString("FILTER_CONDITION"));
                }, username);
                u.setTableFilters(filters);

                return u;
            }, username);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean passwordMatches(String raw, String stored) {
        if (stored == null) return false;
        if (stored.startsWith("{noop}")) {
            return raw.equals(stored.substring(6));
        }
        String hash = stored.startsWith("{bcrypt}") ? stored.substring(8) : stored;
        if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            try { return bcrypt.matches(raw, hash); } catch (Exception e) { return false; }
        }
        throw new IllegalArgumentException("Unrecognized password format for stored credential.");
    }

    /**
     * The user driving the current request. If auth is disabled, returns a synthetic
     * full-access admin so downstream checks uniformly "just work".
     */
    public User effectiveUser(HttpServletRequest req) {
        if (!isEnabled()) return superUser();
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        // Check for cached full User object first
        User cached = (User) session.getAttribute(SESSION_USER_OBJ);
        if (cached != null) return cached;
        // Fall back to username lookup
        String name = (String) session.getAttribute(SESSION_USER);
        if (name == null) return null;
        User u = loadUserFromDb(name.toLowerCase());
        if (u != null) {
            session.setAttribute(SESSION_USER_OBJ, u);
        }
        return u;
    }

    private User superUser() {
        User u = new User();
        u.setUsername("(auth-disabled)");
        u.setRole("ADMIN");
        u.setPrivileges(Arrays.asList("SELECT", "INSERT", "UPDATE", "DELETE", "EXECUTE_SQL"));
        u.setAllowedTables(Collections.emptyList()); // empty = all
        u.setTableFilters(Collections.emptyMap());
        return u;
    }

    // ---- authorization helpers ----

    public boolean hasPrivilege(User u, String priv) {
        if (u == null || u.getPrivileges() == null) return false;
        for (String p : u.getPrivileges()) {
            if (p != null && p.trim().equalsIgnoreCase(priv)) return true;
        }
        return false;
    }

    /** Whether the user is restricted to a whitelist of tables. */
    public boolean isRestricted(User u) {
        return u != null && u.getAllowedTables() != null && !u.getAllowedTables().isEmpty();
    }

    public java.util.Map<String, String> getTableFilters(User u) {
        return u != null && u.getTableFilters() != null ? u.getTableFilters() : Collections.emptyMap();
    }

    public boolean canSeeTable(User u, String table) {
        if (!isRestricted(u)) return true;
        String t = table.toUpperCase();
        return u.getAllowedTables().stream()
                .anyMatch(x -> x != null && x.trim().equalsIgnoreCase(t));
    }

    /** Keep only the names this user is allowed to see. */
    public List<String> filterNames(User u, List<String> names) {
        if (!isRestricted(u)) return names;
        Set<String> allowed = u.getAllowedTables().stream()
                .map(s -> s.trim().toUpperCase()).collect(Collectors.toSet());
        return names.stream().filter(n -> allowed.contains(n.toUpperCase()))
                .collect(Collectors.toList());
    }

    public void requirePrivilege(User u, String priv) {
        if (!hasPrivilege(u, priv)) {
            throw new SecurityException("You don't have the '" + priv + "' privilege.");
        }
    }

    public void requireTableAccess(User u, String table) {
        if (!canSeeTable(u, table)) {
            throw new SecurityException("You don't have access to table " + table + ".");
        }
    }
}
