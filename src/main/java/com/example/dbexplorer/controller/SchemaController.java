package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.Dtos.*;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.SchemaService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaService schema;
    private final AppProperties props;
    private final AuthService auth;

    public SchemaController(SchemaService schema, AppProperties props, AuthService auth) {
        this.schema = schema;
        this.props = props;
        this.auth = auth;
    }

    /** Overview: schema name, read-only flag, (filtered) object counts, change fingerprint. */
    @GetMapping
    public Map<String, Object> overview(HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        Map<String, Object> m = new HashMap<>();
        m.put("schema", schema.resolveSchema());
        m.put("readOnly", props.isReadOnly());
        m.put("counts", filteredCounts(u));
        m.put("fingerprint", schema.fingerprint());
        return m;
    }

    private Map<String, Object> filteredCounts(User u) {
        if (!auth.isRestricted(u)) return new LinkedHashMap<>(schema.objectCounts());
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("TABLE", filtered(u, "TABLE").size());
        c.put("VIEW", filtered(u, "VIEW").size());
        for (String t : new String[]{"PROCEDURE", "FUNCTION", "PACKAGE", "TRIGGER", "SEQUENCE"}) {
            c.put(t, 0); // restricted users only see their whitelisted tables/views
        }
        return c;
    }

    private List<DbObject> filtered(User u, String type) {
        List<DbObject> objs = schema.listObjects(type);
        if (!auth.isRestricted(u)) return objs;
        if (!type.equalsIgnoreCase("TABLE") && !type.equalsIgnoreCase("VIEW"))
            return Collections.emptyList();
        return objs.stream().filter(o -> auth.canSeeTable(u, o.name)).collect(Collectors.toList());
    }

    /** List object names for a type, filtered to what the user may see. */
    @GetMapping("/objects")
    public List<DbObject> objects(@RequestParam String type, HttpServletRequest http) {
        return filtered(auth.effectiveUser(http), type);
    }

    /** Full structure of a table or view. */
    @GetMapping("/table/{name}")
    public TableDetail table(@PathVariable String name, HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requireTableAccess(u, name);
        return schema.getTableDetail(name);
    }

    /** Source/DDL of a code object (procedure, function, package, trigger, view). */
    @GetMapping("/source/{name}")
    public Map<String, String> source(@PathVariable String name, @RequestParam String type,
                                      HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        if (type.equalsIgnoreCase("VIEW")) {
            auth.requireTableAccess(u, name);
        } else if (auth.isRestricted(u)) {
            throw new SecurityException("You don't have access to code objects.");
        }
        Map<String, String> m = new HashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("source", schema.getSource(name, type));
        return m;
    }
}
