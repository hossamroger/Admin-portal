package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.ApiResponse;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.DataService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataService data;
    private final AuthService auth;

    public DataController(DataService data, AuthService auth) {
        this.data = data;
        this.auth = auth;
    }

    /** Insert a row. Body: { "values": { "COL": value, ... } } */
    @PostMapping("/{table}")
    public ApiResponse insert(@PathVariable String table,
                                      @RequestBody Map<String, Object> body,
                                      HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "INSERT");
        auth.requireTableAccess(u, table);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) body.get("values");
        http.setAttribute("auditDetail", "columns=" + (values != null ? values.keySet() : "{}"));
        int n = data.insert(table, values, auth.getTableFilters(u));
        return result(n, "inserted");
    }

    /** Update a row. Body: { "key": {pk...}, "values": {changed...} } */
    @PutMapping("/{table}")
    public ApiResponse update(@PathVariable String table,
                                      @RequestBody Map<String, Object> body,
                                      HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "UPDATE");
        auth.requireTableAccess(u, table);
        @SuppressWarnings("unchecked")
        Map<String, Object> key = (Map<String, Object>) body.get("key");
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) body.get("values");
        http.setAttribute("auditDetail", "key=" + key + " set=" + (values != null ? values.keySet() : "{}"));
        int n = data.update(table, key, values, auth.getTableFilters(u));
        return result(n, "updated");
    }

    /** Delete a row. Body: { "key": {pk...} } */
    @DeleteMapping("/{table}")
    public ApiResponse delete(@PathVariable String table,
                                      @RequestBody Map<String, Object> body,
                                      HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "DELETE");
        auth.requireTableAccess(u, table);
        @SuppressWarnings("unchecked")
        Map<String, Object> key = (Map<String, Object>) body.get("key");
        http.setAttribute("auditDetail", "key=" + key);
        int n = data.delete(table, key, auth.getTableFilters(u));
        return result(n, "deleted");
    }

    private ApiResponse result(int n, String action) {
        // Preserve the legacy top-level {affected, action} fields for clients.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("affected", n);
        data.put("action", action);
        return ApiResponse.ok(action, data);
    }
}
