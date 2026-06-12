package com.example.dbexplorer.security;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.config.CrudEntities;
import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import com.example.dbexplorer.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;

/**
 * Centralised authorization for the controller layer.
 *
 * <p>Performs the privilege + per-user table-whitelist checks consistently and
 * returns the resolved {@code (entity/table, rowFilter)} tuple. Both the generic
 * CRUD controller and the bespoke controllers (service-config, donation, data)
 * route through here so no endpoint can be weaker than another.
 */
@Component
public class AccessResolver {

    private final AuthService auth;

    public AccessResolver(AuthService auth) {
        this.auth = auth;
    }

    /** Resolved access for a request: the table (and CRUD entity, if any) plus its row-level filter. */
    public static final class Access {
        public final CrudEntity entity;   // null for bespoke controllers
        public final String table;
        public final String rowFilter;

        Access(CrudEntity entity, String table, String rowFilter) {
            this.entity = entity;
            this.table = table;
            this.rowFilter = rowFilter;
        }
    }

    /**
     * Resolve a registered CRUD entity: validates the entity exists and supports
     * the operation, then enforces privilege + table access.
     */
    public Access resolveEntity(String entityName, String op, HttpServletRequest http) {
        CrudEntity e = CrudEntities.get(entityName);
        if (e == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown entity: " + entityName);
        if (!e.ops.contains(op))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, op + " not allowed for " + entityName);
        String rowFilter = requireAccess(e.table, op, http);
        return new Access(e, e.table, rowFilter);
    }

    /**
     * Resolve access for a raw table name (bespoke controllers). Enforces
     * privilege + table access and returns the row filter.
     */
    public Access resolveTable(String table, String op, HttpServletRequest http) {
        String rowFilter = requireAccess(table, op, http);
        return new Access(null, table, rowFilter);
    }

    /** Privilege + table-whitelist check; returns the user's row filter for {@code table} (may be null). */
    public String requireAccess(String table, String op, HttpServletRequest http) {
        User u = auth.effectiveUser(http);
        auth.requirePrivilege(u, op);
        auth.requireTableAccess(u, table);
        return auth.getTableFilters(u).get(table.toUpperCase());
    }
}
