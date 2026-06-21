package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiResponse;
import com.example.dbexplorer.dto.EntityDtos.*;
import com.example.dbexplorer.security.AccessResolver;
import com.example.dbexplorer.service.EntityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/entity")
public class EntityController {

    private static final String ENTITY_TABLE = "ENTITY_LKP";

    private final EntityService svc;
    private final AccessResolver access;

    public EntityController(EntityService svc, AccessResolver access) {
        this.svc    = svc;
        this.access = access;
    }

    @GetMapping
    public EntityListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest http) {
        access.requireAccess(ENTITY_TABLE, "SELECT", http);
        return svc.list(search, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityDto> get(@PathVariable long id, HttpServletRequest http) {
        access.requireAccess(ENTITY_TABLE, "SELECT", http);
        return ResponseEntity.ok(svc.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody EntityDto dto, HttpServletRequest http) {
        access.requireAccess(ENTITY_TABLE, "INSERT", http);
        long id = svc.create(dto);
        return ResponseEntity.ok(ApiResponse.ok("Entity created", "id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> update(@PathVariable long id,
                                               @RequestBody EntityDto dto,
                                               HttpServletRequest http) {
        access.requireAccess(ENTITY_TABLE, "UPDATE", http);
        svc.update(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Entity updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable long id, HttpServletRequest http) {
        access.requireAccess(ENTITY_TABLE, "DELETE", http);
        svc.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Entity deleted"));
    }
}
