package com.example.dbexplorer.security;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test void mintsRequestIdWhenNoneSupplied() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/schema");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        String id = res.getHeader(RequestIdFilter.HEADER);
        assertNotNull(id);
        assertFalse(id.isEmpty());
        // MDC must be cleared after the request completes
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test void honoursIncomingRequestId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/schema");
        req.addHeader(RequestIdFilter.HEADER, "gateway-abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertEquals("gateway-abc-123", res.getHeader(RequestIdFilter.HEADER));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test void bindsIdToMdcDuringChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/schema");
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seen = new String[1];
        filter.doFilter(req, res, (request, response) -> seen[0] = MDC.get(RequestIdFilter.MDC_KEY));

        assertNotNull(seen[0], "requestId should be present in MDC while the chain runs");
        assertEquals(res.getHeader(RequestIdFilter.HEADER), seen[0]);
    }
}
