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

    @GetMapping("/{processCode}")
    public ResponseEntity<PayCodePayload> get(@PathVariable String processCode, HttpServletRequest http) {
        requireSelect(http);
        PayCodePayload r = svc.get(processCode);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody PayCodePayload req, HttpServletRequest http) {
        requireInsert(http);
        svc.create(req);
        return ok("Payment code created");
    }

    @PutMapping("/{processCode}")
    public ResponseEntity<ApiResponse> update(@PathVariable String processCode,
                                               @RequestBody PayCodePayload req,
                                               HttpServletRequest http) {
        requireUpdate(http);
        svc.update(processCode, req);
        return ok("Payment code updated");
    }

    @DeleteMapping("/{processCode}")
    public ResponseEntity<ApiResponse> delete(@PathVariable String processCode, HttpServletRequest http) {
        requireDelete(http);
        svc.delete(processCode);
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
