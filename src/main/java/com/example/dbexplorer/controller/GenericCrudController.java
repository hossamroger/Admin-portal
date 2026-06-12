package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiResponse;
import com.example.dbexplorer.security.AccessResolver;
import com.example.dbexplorer.security.AccessResolver.Access;
import com.example.dbexplorer.service.GenericCrudService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Generic CRUD endpoint for whitelisted entities (see CrudEntities).
 * Each entity supports only the operations declared in its registry entry.
 * Per-user table whitelists and row-level filters are enforced just like
 * the rest of the app.
 */
@RestController
@RequestMapping("/api/crud")
public class GenericCrudController {

    private final GenericCrudService svc;
    private final AccessResolver access;

    public GenericCrudController(GenericCrudService svc, AccessResolver access) {
        this.svc    = svc;
        this.access = access;
    }

    @GetMapping("/{entity}")
    public Map<String, Object> list(
            @PathVariable String entity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "SELECT", http);
        return svc.list(a.entity, search, Math.max(page, 0),
            Math.min(Math.max(pageSize, 1), 500), a.rowFilter, sort, dir);
    }

    @GetMapping("/{entity}/lookup/{name}")
    public List<Map<String, Object>> lookup(
            @PathVariable String entity, @PathVariable String name, HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "SELECT", http);
        return svc.lookup(a.entity, name);
    }

    /** Entity schema for config-driven UI. More specific than /{entity}/{id}. */
    @GetMapping("/{entity}/_meta")
    public Map<String, Object> meta(@PathVariable String entity, HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "SELECT", http);
        return svc.describe(a.entity);
    }

    @GetMapping("/{entity}/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String entity, @PathVariable String id, HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "SELECT", http);
        Map<String, Object> row = svc.get(a.entity, id, a.rowFilter);
        return row == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(row);
    }

    @PostMapping("/{entity}")
    public ResponseEntity<ApiResponse> create(
            @PathVariable String entity, @RequestBody Map<String, Object> values,
            HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "INSERT", http);
        Object id = svc.create(a.entity, values);
        return ResponseEntity.ok(ApiResponse.ok("Created", "id", id));
    }

    @PutMapping("/{entity}/{id}")
    public ResponseEntity<ApiResponse> update(
            @PathVariable String entity, @PathVariable String id,
            @RequestBody Map<String, Object> values, HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "UPDATE", http);
        svc.update(a.entity, id, values, a.rowFilter);
        return ResponseEntity.ok(ApiResponse.ok("Updated"));
    }

    @DeleteMapping("/{entity}/{id}")
    public ResponseEntity<ApiResponse> delete(
            @PathVariable String entity, @PathVariable String id, HttpServletRequest http) {
        Access a = access.resolveEntity(entity, "DELETE", http);
        svc.delete(a.entity, id, a.rowFilter);
        return ResponseEntity.ok(ApiResponse.ok("Deleted"));
    }
}
