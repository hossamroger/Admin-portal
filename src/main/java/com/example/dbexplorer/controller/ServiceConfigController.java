package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import com.example.dbexplorer.service.AuthService;
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

    private final ServiceConfigService svc;
    private final AuthService auth;

    public ServiceConfigController(ServiceConfigService svc, AuthService auth) {
        this.svc  = svc;
        this.auth = auth;
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
    public ResponseEntity<Map<String, Object>> create(@RequestBody ServiceConfigRequest req, HttpServletRequest http) {
        requireInsert(http);
        svc.create(req);
        return ok("Service created");
    }

    // ── Update (full) ─────────────────────────────────────────────────────────

    @PutMapping("/{code}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String code,
                                                       @RequestBody ServiceConfigRequest req,
                                                       HttpServletRequest http) {
        requireUpdate(http);
        svc.update(code, req);
        return ok("Service updated");
    }

    // ── Per-tab partial saves ─────────────────────────────────────────────────

    @PutMapping("/{code}/steps")
    public ResponseEntity<Map<String, Object>> saveSteps(@PathVariable String code,
                                                          @RequestBody List<StepDto> steps,
                                                          HttpServletRequest http) {
        requireUpdate(http);
        svc.saveSteps(code, steps);
        return ok("Steps saved");
    }

    @PutMapping("/{code}/fees")
    public ResponseEntity<Map<String, Object>> saveFees(@PathVariable String code,
                                                         @RequestBody List<FeeDto> fees,
                                                         HttpServletRequest http) {
        requireUpdate(http);
        svc.saveFees(code, fees);
        return ok("Fees saved");
    }

    @PutMapping("/{code}/docs")
    public ResponseEntity<Map<String, Object>> saveDocs(@PathVariable String code,
                                                         @RequestBody List<RequiredDocDto> docs,
                                                         HttpServletRequest http) {
        requireUpdate(http);
        svc.saveDocs(code, docs);
        return ok("Required documents saved");
    }

    @PutMapping("/{code}/depts")
    public ResponseEntity<Map<String, Object>> saveDepts(@PathVariable String code,
                                                          @RequestBody List<RelatedDeptDto> depts,
                                                          HttpServletRequest http) {
        requireUpdate(http);
        svc.saveDepts(code, depts);
        return ok("Providers saved");
    }

    @PutMapping("/{code}/audience")
    public ResponseEntity<Map<String, Object>> saveAudience(@PathVariable String code,
                                                             @RequestBody List<TargetAudienceDto> audiences,
                                                             HttpServletRequest http) {
        requireUpdate(http);
        svc.saveAudiences(code, audiences);
        return ok("Target audience saved");
    }

    @PutMapping("/{code}/confirmation")
    public ResponseEntity<Map<String, Object>> saveConfirmation(@PathVariable String code,
                                                                 @RequestBody List<ConfirmationScreenConfigDto> cfgs,
                                                                 HttpServletRequest http) {
        requireUpdate(http);
        svc.saveConfirmation(code, cfgs);
        return ok("Confirmation screens saved");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{code}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String code, HttpServletRequest http) {
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
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "SELECT");
    }

    private void requireInsert(HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "INSERT");
    }

    private void requireUpdate(HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "UPDATE");
    }

    private void requireDelete(HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "DELETE");
    }

    private ResponseEntity<Map<String, Object>> ok(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", message);
        return ResponseEntity.ok(m);
    }
}
