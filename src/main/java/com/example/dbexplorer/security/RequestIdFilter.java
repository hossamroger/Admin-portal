package com.example.dbexplorer.security;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation id to every request so log lines for one request can be
 * tied together. Honours an incoming X-Request-Id (e.g. from a gateway) or mints
 * a short one, exposes it on the response header, and binds it to the SLF4J MDC
 * under "requestId" for the logging pattern. Runs before AuthFilter (@Order 1).
 */
@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader(HEADER);
        String requestId = (incoming != null && !incoming.trim().isEmpty())
            ? incoming.trim()
            : UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, requestId);
        res.setHeader(HEADER, requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
