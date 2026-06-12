package com.example.dbexplorer.security;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.dto.ApiError;
import com.example.dbexplorer.service.AuditService;
import com.example.dbexplorer.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Guards every /api/** call and records an audit entry for each one.
 *
 * Unauthenticated requests get a 401 so the SPA can show the login screen. /api/login is
 * open. As a lightweight CSRF mitigation, state-changing requests must carry the
 * X-Requested-With header (which browsers won't attach on a simple cross-site request).
 *
 * Auditing happens in a finally block so it captures the final HTTP status (success or
 * failure) of every action, and only enqueues the event — see AuditService for why this
 * adds no measurable latency.
 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService auth;
    private final AuditService audit;
    private final AppProperties props;
    private final ObjectMapper json = new ObjectMapper();

    public AuthFilter(AuthService auth, AuditService audit, AppProperties props) {
        this.auth = auth;
        this.audit = audit;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        boolean isApi = path.startsWith("/api/");

        // Only the API is guarded/audited; static assets (UI, CSS, JS) pass straight through.
        if (!isApi || !auth.isEnabled()) {
            chain.doFilter(req, res);
            return;
        }

        long t0 = System.nanoTime();
        try {
            if (path.equals("/api/login")) {
                if (isStateChanging(req) && missingXrw(req)) { reject(res, 403, "Missing X-Requested-With header"); return; }
                chain.doFilter(req, res);
                return;
            }
            if (auth.effectiveUser(req) == null) {
                reject(res, 401, "Authentication required");
                return;
            }
            if (isStateChanging(req) && missingXrw(req)) {
                reject(res, 403, "Missing X-Requested-With header");
                return;
            }
            chain.doFilter(req, res);
        } finally {
            recordAudit(req, res, t0);
        }
    }

    /** Enqueue an audit event for this request. Cheap and exception-safe. */
    private void recordAudit(HttpServletRequest req, HttpServletResponse res, long t0) {
        try {
            String path = req.getRequestURI();
            // background/auth-check polling is not a user action — skip the noise
            if (path.equals("/api/me") || path.equals("/api/schema/fingerprint")) return;

            String method = req.getMethod();
            boolean read = "GET".equals(method) || "HEAD".equals(method);
            if (read && !props.getAudit().isLogReads()) return;

            long ms = (System.nanoTime() - t0) / 1_000_000L;
            int status = res.getStatus();
            boolean success = status > 0 && status < 400;

            // who: username set by the login controller (so failed logins are attributed),
            // otherwise the session user, otherwise anonymous.
            String user;
            Object attrUser = req.getAttribute("auditUsername");
            if (attrUser != null) {
                user = attrUser.toString();
            } else {
                User u = auth.effectiveUser(req);
                user = (u != null) ? u.getUsername() : "anonymous";
            }

            String[] at = classify(method, path, req);
            Object detail = req.getAttribute("auditDetail");

            audit.record(user, at[0], at[1], detail != null ? detail.toString() : null,
                    success, status, req.getRemoteAddr(), ms);
        } catch (Exception ignore) {
            // auditing must never affect the response
        }
    }

    /** Map an HTTP method + path to a human-readable {action, target}. */
    private String[] classify(String method, String path, HttpServletRequest req) {
        String[] p = path.split("/");           // ["", "api", ...]
        String last = p.length > 0 ? p[p.length - 1] : "";

        if (path.equals("/api/login"))  return new String[]{"LOGIN", null};
        if (path.equals("/api/logout")) return new String[]{"LOGOUT", null};
        if (path.equals("/api/query/run")) return new String[]{"RUN_SQL", null};
        if (path.startsWith("/api/query/data/")) return new String[]{"BROWSE_DATA", dec(last)};
        if (path.startsWith("/api/data/")) {
            String a = "POST".equals(method) ? "INSERT_ROW"
                     : "PUT".equals(method) ? "UPDATE_ROW"
                     : "DELETE".equals(method) ? "DELETE_ROW" : method;
            return new String[]{a, dec(last)};
        }
        if (path.startsWith("/api/donation-project/") && "PUT".equals(method)) {
            String a = path.endsWith("/amounts") ? "SAVE_DONATION_AMOUNTS"
                     : path.endsWith("/details") ? "SAVE_DONATION_DETAILS" : method;
            return new String[]{a, p.length > 3 ? dec(p[3]) : null};
        }
        if (path.startsWith("/api/attachments/upload")) return new String[]{"UPLOAD_FILE", null};
        if (path.equals("/api/schema")) return new String[]{"VIEW_OVERVIEW", null};
        if (path.startsWith("/api/schema/objects")) return new String[]{"LIST_OBJECTS", req.getParameter("type")};
        if (path.startsWith("/api/schema/table/")) return new String[]{"VIEW_TABLE", dec(last)};
        if (path.startsWith("/api/schema/source/")) return new String[]{"VIEW_SOURCE", dec(last)};
        return new String[]{method + " " + path, null};
    }

    private static String dec(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private boolean isStateChanging(HttpServletRequest req) {
        String m = req.getMethod();
        return !("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m));
    }

    private boolean missingXrw(HttpServletRequest req) {
        return req.getHeader("X-Requested-With") == null;
    }

    /** Emit the shared {@link ApiError} envelope, mirroring GlobalExceptionHandler. */
    private void reject(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        String type = HttpStatus.valueOf(status).getReasonPhrase();
        ApiError body = ApiError.of(msg, type, status, MDC.get(RequestIdFilter.MDC_KEY));
        res.getWriter().write(json.writeValueAsString(body));
    }
}
