package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.QueryDtos.QueryRequest;
import com.example.dbexplorer.dto.QueryDtos.QueryResult;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.QueryService;
import com.example.dbexplorer.service.SchemaService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService query;
    private final SchemaService schema;
    private final AuthService auth;

    public QueryController(QueryService query, SchemaService schema, AuthService auth) {
        this.query = query;
        this.schema = schema;
        this.auth = auth;
    }

    /** Run an arbitrary SQL script — requires the EXECUTE_SQL privilege. */
    @PostMapping("/run")
    public List<QueryResult> run(@RequestBody QueryRequest req, HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "EXECUTE_SQL");
        http.setAttribute("auditDetail", req.sql);
        return query.runScript(req.sql == null ? "" : req.sql, req.maxRows);
    }

    /** PK generation info: auto-generated vs user-assigned (with next suggested value). */
    @GetMapping("/pk-next/{table}")
    public Map<String, Object> pkNext(@PathVariable String table, HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "SELECT");
        auth.requireTableAccess(u, table);
        return query.pkNextInfo(table);
    }

    /** Paginated, optionally-sorted browse of a table/view's data. */
    @GetMapping("/insights/{table}")
    public Map<String, Object> insights(@PathVariable String table, HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requireTableAccess(u, table);
        return query.getTableInsights(table, auth.getTableFilters(u));
    }

    @GetMapping("/data/{table}")
    public Map<String, Object> data(@PathVariable String table,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int pageSize,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(required = false) String dir,
                                    HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, "SELECT");
        auth.requireTableAccess(u, table);

        Map<String,String> tableFilters = auth.getTableFilters(u);
        QueryResult result = query.browseTable(table, page, pageSize, sort, dir, tableFilters);
        long total = query.countTable(table, tableFilters);

        Map<String, Object> m = new HashMap<>();
        m.put("result", result);
        m.put("total", total);
        m.put("page", page);
        m.put("pageSize", pageSize);
        m.put("sort", sort);
        m.put("dir", dir);
        m.put("pkColumns", schema.getPkColumns(table));
        Map<String, Boolean> can = new HashMap<>();
        can.put("insert", auth.hasPrivilege(u, "INSERT"));
        can.put("update", auth.hasPrivilege(u, "UPDATE"));
        can.put("delete", auth.hasPrivilege(u, "DELETE"));
        m.put("can", can);
        return m;
    }
}
