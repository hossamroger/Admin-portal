package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.ProcessStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/process-status")
public class ProcessStatusController {

    private final ProcessStatusService svc;
    private final AuthService auth;

    public ProcessStatusController(ProcessStatusService svc, AuthService auth) {
        this.svc  = svc;
        this.auth = auth;
    }

    @GetMapping
    public ProcessStatusListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest http) {
        requireSelect(http);
        return svc.list(search, page, pageSize);
    }

    @GetMapping("/next-id")
    public ResponseEntity<Map<String, Object>> nextId(HttpServletRequest http) {
        requireSelect(http);
        Map<String, Object> r = new HashMap<>();
        r.put("nextId", svc.nextId());
        return ResponseEntity.ok(r);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessStatusDto> get(@PathVariable long id, HttpServletRequest http) {
        requireSelect(http);
        ProcessStatusDto d = svc.get(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody ProcessStatusDto dto, HttpServletRequest http) {
        requireInsert(http);
        svc.create(dto);
        return ok("Status created");
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long id, @RequestBody ProcessStatusDto dto, HttpServletRequest http) {
        requireUpdate(http);
        svc.update(id, dto);
        return ok("Status updated");
    }

    @GetMapping("/lookups/msgs")
    public List<ProcessStatusMsgDto> lookupMsgs(HttpServletRequest http) {
        requireSelect(http);
        return svc.lookupMsgs();
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

    private ResponseEntity<Map<String, Object>> ok(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", message);
        return ResponseEntity.ok(m);
    }
}
