package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.config.CrudEntities;
import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.GenericCrudService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic CRUD endpoint for whitelisted entities (see CrudEntities).
 * Each entity supports only the operations declared in its registry entry.
 */
@RestController
@RequestMapping("/api/crud")
public class GenericCrudController {

    private final GenericCrudService svc;
    private final AuthService auth;

    public GenericCrudController(GenericCrudService svc, AuthService auth) {
        this.svc  = svc;
        this.auth = auth;
    }

    @GetMapping("/{entity}")
    public Map<String, Object> list(
            @PathVariable String entity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest http) {
        CrudEntity e = resolve(entity, "SELECT", http);
        return svc.list(e, search, page, Math.min(Math.max(pageSize, 1), 500));
    }

    @GetMapping("/{entity}/lookup/{name}")
    public List<Map<String, Object>> lookup(
            @PathVariable String entity, @PathVariable String name, HttpServletRequest http) {
        CrudEntity e = resolve(entity, "SELECT", http);
        return svc.lookup(e, name);
    }

    @GetMapping("/{entity}/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String entity, @PathVariable String id, HttpServletRequest http) {
        CrudEntity e = resolve(entity, "SELECT", http);
        Map<String, Object> row = svc.get(e, id);
        return row == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(row);
    }

    @PostMapping("/{entity}")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String entity, @RequestBody Map<String, Object> values,
            HttpServletRequest http) {
        CrudEntity e = resolve(entity, "INSERT", http);
        Object id = svc.create(e, values);
        Map<String, Object> r = new HashMap<>();
        r.put("message", "Created");
        r.put("id", id);
        return ResponseEntity.ok(r);
    }

    @PutMapping("/{entity}/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String entity, @PathVariable String id,
            @RequestBody Map<String, Object> values, HttpServletRequest http) {
        CrudEntity e = resolve(entity, "UPDATE", http);
        svc.update(e, id, values);
        Map<String, Object> r = new HashMap<>();
        r.put("message", "Updated");
        return ResponseEntity.ok(r);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CrudEntity resolve(String entity, String op, HttpServletRequest http) {
        CrudEntity e = CrudEntities.get(entity);
        if (e == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown entity: " + entity);
        if (!e.ops.contains(op))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, op + " not allowed for " + entity);
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, op);
        return e;
    }
}
