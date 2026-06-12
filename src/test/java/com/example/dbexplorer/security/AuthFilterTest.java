package com.example.dbexplorer.security;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.service.AuditService;
import com.example.dbexplorer.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the servlet-level security gate: authentication (401),
 * CSRF header enforcement (403), the open /api/login path, and the
 * non-API / auth-disabled passthroughs.
 */
class AuthFilterTest {

    private AuthService auth;
    private AuditService audit;
    private AppProperties props;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        audit = mock(AuditService.class);
        props = new AppProperties();
        filter = new AuthFilter(auth, audit, props);
        when(auth.isEnabled()).thenReturn(true);
    }

    private User user(String name) {
        User u = new User();
        u.setUsername(name);
        return u;
    }

    private MockFilterChain doFilter(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return chain;
    }

    // ── Passthroughs ───────────────────────────────────────────────────────────

    @Test void nonApiRequestPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertNotNull(chain.getRequest(), "non-API request should reach the chain");
        verify(auth, never()).effectiveUser(any());
    }

    @Test void authDisabledPassesThrough() throws Exception {
        when(auth.isEnabled()).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/crud/donation-project");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertNotNull(chain.getRequest(), "when auth disabled, request should reach the chain");
    }

    // ── Authentication ─────────────────────────────────────────────────────────

    @Test void unauthenticatedApiGetIsRejected401() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/schema");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest(), "rejected request must not reach the chain");
    }

    @Test void authenticatedGetPasses() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(user("alice"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/schema");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    // ── CSRF (X-Requested-With) ──────────────────────────────────────────────────

    @Test void stateChangingWithoutXrwIsRejected403() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(user("alice"));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/crud/donation-project");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertEquals(403, res.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void stateChangingWithXrwPasses() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(user("alice"));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/crud/donation-project");
        req.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test void authedGetDoesNotRequireXrw() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(user("alice"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/crud/donation-project");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    // ── /api/login is open but still CSRF-guarded ───────────────────────────────

    @Test void loginPostWithXrwReachesChainWithoutSession() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/login");
        req.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertNotNull(chain.getRequest(), "login must be reachable without an existing session");
        assertEquals(200, res.getStatus());
    }

    @Test void loginPostWithoutXrwIsRejected403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = doFilter(req, res);
        assertEquals(403, res.getStatus());
        assertNull(chain.getRequest());
    }

    // ── Auditing ────────────────────────────────────────────────────────────────

    @Test void auditRecordedForAuthedAction() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(user("alice"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/schema");
        MockHttpServletResponse res = new MockHttpServletResponse();
        doFilter(req, res);
        verify(audit, times(1)).record(eq("alice"), anyString(), any(), any(), anyBoolean(), anyInt(), any(), anyLong());
    }

    @Test void mePollingIsNotAudited() throws Exception {
        when(auth.effectiveUser(any())).thenReturn(user("alice"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        doFilter(req, res);
        verify(audit, never()).record(any(), any(), any(), any(), anyBoolean(), anyInt(), any(), anyLong());
    }
}
