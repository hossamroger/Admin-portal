package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.HomeBannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/home-banner")
public class HomeBannerController {

    private final HomeBannerService svc;
    private final AuthService auth;

    public HomeBannerController(HomeBannerService svc, AuthService auth) {
        this.svc  = svc;
        this.auth = auth;
    }

    @GetMapping
    public HomeBannerListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest http) {
        requireSelect(http);
        return svc.list(search, platform, page, pageSize);
    }

    @GetMapping("/next-order")
    public ResponseEntity<Map<String, Object>> nextOrder(HttpServletRequest http) {
        requireSelect(http);
        Map<String, Object> r = new HashMap<>();
        r.put("nextOrder", svc.nextBannerOrder());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HomeBannerDto> get(@PathVariable long id, HttpServletRequest http) {
        requireSelect(http);
        HomeBannerDto d = svc.get(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody HomeBannerDto dto, HttpServletRequest http) {
        requireInsert(http);
        svc.create(dto);
        return ok("Banner created");
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long id, @RequestBody HomeBannerDto dto, HttpServletRequest http) {
        requireUpdate(http);
        svc.update(id, dto);
        return ok("Banner updated");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireSelect(HttpServletRequest http) {
        User u = auth.effectiveUser(http); auth.requirePrivilege(u, "SELECT");
    }
    private void requireInsert(HttpServletRequest http) {
        User u = auth.effectiveUser(http); auth.requirePrivilege(u, "INSERT");
    }
    private void requireUpdate(HttpServletRequest http) {
        User u = auth.effectiveUser(http); auth.requirePrivilege(u, "UPDATE");
    }

    private ResponseEntity<Map<String, Object>> ok(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", msg);
        return ResponseEntity.ok(m);
    }
}
