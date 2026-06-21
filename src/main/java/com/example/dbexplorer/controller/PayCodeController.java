package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiResponse;
import com.example.dbexplorer.dto.PayCodeDtos.*;
import com.example.dbexplorer.security.AccessResolver;
import com.example.dbexplorer.service.PayCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/pay-code")
public class PayCodeController {

    private static final String PAY_CODE_TABLE = "LKP_PAY_CODE";

    private final PayCodeService svc;
    private final AccessResolver access;

    public PayCodeController(PayCodeService svc, AccessResolver access) {
        this.svc    = svc;
        this.access = access;
    }

    @GetMapping
    public PayCodeListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest http) {
        requireSelect(http);
        return svc.list(search, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayCodePayload> get(@PathVariable long id, HttpServletRequest http) {
        requireSelect(http);
        PayCodePayload r = svc.get(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody PayCodePayload req, HttpServletRequest http) {
        requireInsert(http);
        long id = svc.create(req);
        return ResponseEntity.ok(ApiResponse.ok("Payment code created", "id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> update(@PathVariable long id,
                                               @RequestBody PayCodePayload req,
                                               HttpServletRequest http) {
        requireUpdate(http);
        svc.update(id, req);
        return ok("Payment code updated");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable long id, HttpServletRequest http) {
        requireDelete(http);
        svc.delete(id);
        return ok("Payment code deleted");
    }

    @GetMapping("/lookups/entities")
    public List<EntityLookup> lookupEntities(HttpServletRequest http) {
        requireSelect(http);
        return svc.lookupEntities();
    }

    private void requireSelect(HttpServletRequest http) { access.requireAccess(PAY_CODE_TABLE, "SELECT", http); }
    private void requireInsert(HttpServletRequest http) { access.requireAccess(PAY_CODE_TABLE, "INSERT", http); }
    private void requireUpdate(HttpServletRequest http) { access.requireAccess(PAY_CODE_TABLE, "UPDATE", http); }
    private void requireDelete(HttpServletRequest http) { access.requireAccess(PAY_CODE_TABLE, "DELETE", http); }

    private ResponseEntity<ApiResponse> ok(String msg) {
        return ResponseEntity.ok(ApiResponse.ok(msg));
    }
}
