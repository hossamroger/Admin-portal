package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiResponse;
import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import com.example.dbexplorer.security.AccessResolver;
import com.example.dbexplorer.service.ServiceConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-config")
public class ServiceConfigController {

    /** Backing table for service configuration; access is gated on it. */
    private static final String SERVICE_TABLE = "BPM_PROCESSES_INFO";

    private final ServiceConfigService svc;
    private final AccessResolver access;

    public ServiceConfigController(ServiceConfigService svc, AccessResolver access) {
        this.svc    = svc;
        this.access = access;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ServiceListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest http) {
        requireSelect(http);
        return svc.list(search, status, type, page, pageSize);
    }

    // ── Get one ───────────────────────────────────────────────────────────────

    @GetMapping("/{code}")
    public ResponseEntity<ServiceConfigResponse> get(@PathVariable String code, HttpServletRequest http) {
        requireSelect(http);
        ServiceConfigResponse r = svc.get(code);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    // ── Next order value ──────────────────────────────────────────────────────

    @GetMapping("/next-order")
    public ResponseEntity<Map<String, Object>> nextOrder(HttpServletRequest http) {
        requireSelect(http);
        long next = svc.nextProcessOrder();
        Map<String, Object> r = new HashMap<>();
        r.put("nextOrder", next);
        return ResponseEntity.ok(r);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody ServiceConfigRequest req, HttpServletRequest http) {
        requireInsert(http);
        svc.create(req);
        return ok("Service created");
    }

    // ── Update (full) ─────────────────────────────────────────────────────────

    @PutMapping("/{code}")
    public ResponseEntity<ApiResponse> update(@PathVariable String code,
                                                       @RequestBody ServiceConfigRequest req,
                                                       HttpServletRequest http) {
        requireUpdate(http);
        svc.update(code, req);
        return ok("Service updated");
    }

    // ── Per-tab partial saves ─────────────────────────────────────────────────

    @PutMapping("/{code}/steps")
    public ResponseEntity<ApiResponse> saveSteps(@PathVariable String code,
                                                          @RequestBody List<StepDto> steps,
                                                          HttpServletRequest http) {
        requireUpdate(http);
        svc.saveSteps(code, steps);
        return ok("Steps saved");
    }

    @PutMapping("/{code}/fees")
    public ResponseEntity<ApiResponse> saveFees(@PathVariable String code,
                                                         @RequestBody List<FeeDto> fees,
                                                         HttpServletRequest http) {
        requireUpdate(http);
        svc.saveFees(code, fees);
        return ok("Fees saved");
    }

    @PutMapping("/{code}/docs")
    public ResponseEntity<ApiResponse> saveDocs(@PathVariable String code,
                                                         @RequestBody List<RequiredDocDto> docs,
                                                         HttpServletRequest http) {
        requireUpdate(http);
        svc.saveDocs(code, docs);
        return ok("Required documents saved");
    }

    @PutMapping("/{code}/depts")
    public ResponseEntity<ApiResponse> saveDepts(@PathVariable String code,
                                                          @RequestBody List<RelatedDeptDto> depts,
                                                          HttpServletRequest http) {
        requireUpdate(http);
        svc.saveDepts(code, depts);
        return ok("Providers saved");
    }

    @PutMapping("/{code}/audience")
    public ResponseEntity<ApiResponse> saveAudience(@PathVariable String code,
                                                             @RequestBody List<TargetAudienceDto> audiences,
                                                             HttpServletRequest http) {
        requireUpdate(http);
        svc.saveAudiences(code, audiences);
        return ok("Target audience saved");
    }

    @PutMapping("/{code}/confirmation")
    public ResponseEntity<ApiResponse> saveConfirmation(@PathVariable String code,
                                                                 @RequestBody List<ConfirmationScreenConfigDto> cfgs,
                                                                 HttpServletRequest http) {
        requireUpdate(http);
        svc.saveConfirmation(code, cfgs);
        return ok("Confirmation screens saved");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{code}")
    public ResponseEntity<ApiResponse> delete(@PathVariable String code, HttpServletRequest http) {
        requireDelete(http);
        svc.delete(code);
        return ok("Service deleted");
    }

    // ── Lookups (dropdown data) ───────────────────────────────────────────────

    @GetMapping("/lookups/audience")
    public List<TargetAudienceLookup> lookupAudience(HttpServletRequest http) {
        requireSelect(http);
        return svc.lookupTargetAudience();
    }

    @GetMapping("/lookups/screen-info")
    public List<ScreenInfoLookup> lookupScreenInfo(HttpServletRequest http) {
        requireSelect(http);
        return svc.lookupScreenInfo();
    }

    @GetMapping("/lookups/components")
    public List<ComponentInfoLookup> lookupComponents(HttpServletRequest http) {
        requireSelect(http);
        return svc.lookupComponents();
    }

    @GetMapping("/lookups/statuses")
    public List<String> lookupStatuses(HttpServletRequest http) {
        requireSelect(http);
        return svc.listDistinctStatuses();
    }

    @GetMapping("/lookups/types")
    public List<String> lookupTypes(HttpServletRequest http) {
        requireSelect(http);
        return svc.listDistinctTypes();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireSelect(HttpServletRequest http) {
        access.requireAccess(SERVICE_TABLE, "SELECT", http);
    }

    private void requireInsert(HttpServletRequest http) {
        access.requireAccess(SERVICE_TABLE, "INSERT", http);
    }

    private void requireUpdate(HttpServletRequest http) {
        access.requireAccess(SERVICE_TABLE, "UPDATE", http);
    }

    private void requireDelete(HttpServletRequest http) {
        access.requireAccess(SERVICE_TABLE, "DELETE", http);
    }

    private ResponseEntity<ApiResponse> ok(String message) {
        return ResponseEntity.ok(ApiResponse.ok(message));
    }
}
