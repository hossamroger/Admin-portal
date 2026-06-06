package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest req) {
        req.setAttribute("auditUsername", body.get("username"));
        if (!auth.isEnabled()) {
            return ResponseEntity.ok(meBody(auth.effectiveUser(req)));
        }
        Optional<User> u = auth.authenticate(body.get("username"), body.get("password"));
        if (!u.isPresent()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }
        // fresh session to avoid fixation
        req.getSession(true).setAttribute(AuthService.SESSION_USER, u.get().getUsername());
        auth.cacheUser(req, u.get());
        return ResponseEntity.ok(meBody(u.get()));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        if (req.getSession(false) != null) req.getSession(false).invalidate();
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return m;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest req) {
        User u = auth.effectiveUser(req);
        if (u == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("username", null);
            m.put("authEnabled", auth.isEnabled());
            return ResponseEntity.ok(m);
        }
        return ResponseEntity.ok(meBody(u));
    }

    private Map<String, Object> meBody(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        m.put("privileges", u.getPrivileges());
        m.put("allowedTables", auth.isRestricted(u) ? u.getAllowedTables() : Collections.emptyList());
        m.put("restricted", auth.isRestricted(u));
        m.put("authEnabled", auth.isEnabled());
        // convenience flags for the UI
        Map<String, Boolean> can = new LinkedHashMap<>();
        can.put("select", auth.hasPrivilege(u, "SELECT"));
        can.put("insert", auth.hasPrivilege(u, "INSERT"));
        can.put("update", auth.hasPrivilege(u, "UPDATE"));
        can.put("delete", auth.hasPrivilege(u, "DELETE"));
        can.put("runSql", auth.hasPrivilege(u, "EXECUTE_SQL"));
        m.put("can", can);
        return m;
    }
}
