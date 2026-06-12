package com.example.dbexplorer.controller;

import com.example.dbexplorer.dto.ApiResponse;
import com.example.dbexplorer.security.AccessResolver;
import com.example.dbexplorer.service.DonationProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/donation-project")
public class DonationProjectController {

    private static final String PROJECTS_TABLE = "DA_DONATION_PROJECTS";
    private static final String AMOUNTS_TABLE  = "DA_DONATION_PROJECTS_AMOUNT";
    private static final String DETAILS_TABLE  = "DA_DONATION_PROJECTS_DETAILS";

    private final DonationProjectService svc;
    private final AccessResolver access;

    public DonationProjectController(DonationProjectService svc, AccessResolver access) {
        this.svc    = svc;
        this.access = access;
    }

    @GetMapping("/{id}/amounts")
    public List<Map<String,Object>> getAmounts(@PathVariable long id, HttpServletRequest http) {
        authorize(http, "SELECT", AMOUNTS_TABLE);
        return svc.listAmounts(id);
    }

    @PutMapping("/{id}/amounts")
    public ResponseEntity<ApiResponse> saveAmounts(
            @PathVariable long id, @RequestBody List<Map<String,Object>> body,
            HttpServletRequest http) {
        authorize(http, "UPDATE", AMOUNTS_TABLE);
        svc.saveAmounts(id, body);
        return ResponseEntity.ok(saved());
    }

    @GetMapping("/{id}/details")
    public List<Map<String,Object>> getDetails(@PathVariable long id, HttpServletRequest http) {
        authorize(http, "SELECT", DETAILS_TABLE);
        return svc.listDetails(id);
    }

    @PutMapping("/{id}/details")
    public ResponseEntity<ApiResponse> saveDetails(
            @PathVariable long id, @RequestBody List<Map<String,Object>> body,
            HttpServletRequest http) {
        authorize(http, "UPDATE", DETAILS_TABLE);
        svc.saveDetails(id, body);
        return ResponseEntity.ok(saved());
    }

    private static ApiResponse saved() {
        return ApiResponse.ok("Saved");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Mirror GenericCrudController access checks: privilege + table whitelist.
     *  The rows always belong to a project, so access to the parent table is
     *  required as well. */
    private void authorize(HttpServletRequest http, String op, String table) {
        access.requireAccess(table, op, http);
        access.requireAccess(PROJECTS_TABLE, op, http);
    }
}
