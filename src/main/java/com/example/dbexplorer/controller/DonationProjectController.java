package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.service.AuthService;
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
    private final AuthService auth;

    public DonationProjectController(DonationProjectService svc, AuthService auth) {
        this.svc  = svc;
        this.auth = auth;
    }

    @GetMapping("/{id}/amounts")
    public List<Map<String,Object>> getAmounts(@PathVariable long id, HttpServletRequest http) {
        authorize(http, "SELECT", AMOUNTS_TABLE);
        return svc.listAmounts(id);
    }

    @PutMapping("/{id}/amounts")
    public ResponseEntity<Map<String,Object>> saveAmounts(
            @PathVariable long id, @RequestBody List<Map<String,Object>> body,
            HttpServletRequest http) {
        authorize(http, "UPDATE", AMOUNTS_TABLE);
        svc.saveAmounts(id, body);
        return ResponseEntity.ok(Map.of("message","Saved"));
    }

    @GetMapping("/{id}/details")
    public List<Map<String,Object>> getDetails(@PathVariable long id, HttpServletRequest http) {
        authorize(http, "SELECT", DETAILS_TABLE);
        return svc.listDetails(id);
    }

    @PutMapping("/{id}/details")
    public ResponseEntity<Map<String,Object>> saveDetails(
            @PathVariable long id, @RequestBody List<Map<String,Object>> body,
            HttpServletRequest http) {
        authorize(http, "UPDATE", DETAILS_TABLE);
        svc.saveDetails(id, body);
        return ResponseEntity.ok(Map.of("message","Saved"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Mirror GenericCrudController.resolve(): privilege + table whitelist checks.
     *  The rows always belong to a project, so access to the parent table is
     *  required as well. */
    private void authorize(HttpServletRequest http, String op, String table) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, op);
        auth.requireTableAccess(u, table);
        auth.requireTableAccess(u, PROJECTS_TABLE);
    }
}
