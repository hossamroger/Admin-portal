package com.example.dbexplorer.controller;

import com.example.dbexplorer.service.DonationProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/donation-project")
public class DonationProjectController {

    private final DonationProjectService svc;

    public DonationProjectController(DonationProjectService svc) { this.svc = svc; }

    @GetMapping("/{id}/amounts")
    public List<Map<String,Object>> getAmounts(@PathVariable long id) {
        return svc.listAmounts(id);
    }

    @PutMapping("/{id}/amounts")
    public ResponseEntity<Map<String,Object>> saveAmounts(
            @PathVariable long id, @RequestBody List<Map<String,Object>> body) {
        svc.saveAmounts(id, body);
        return ResponseEntity.ok(Map.of("message","Saved"));
    }

    @GetMapping("/{id}/details")
    public List<Map<String,Object>> getDetails(@PathVariable long id) {
        return svc.listDetails(id);
    }

    @PutMapping("/{id}/details")
    public ResponseEntity<Map<String,Object>> saveDetails(
            @PathVariable long id, @RequestBody List<Map<String,Object>> body) {
        svc.saveDetails(id, body);
        return ResponseEntity.ok(Map.of("message","Saved"));
    }
}
