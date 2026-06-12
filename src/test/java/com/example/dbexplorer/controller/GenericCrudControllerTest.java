package com.example.dbexplorer.controller;

import com.example.dbexplorer.config.AppProperties.User;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.GenericCrudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the access-control gating in GenericCrudController.resolve:
 * unknown entity (404), unsupported operation (403), privilege/table denial
 * (propagated SecurityException → 403), and the happy path delegation.
 */
class GenericCrudControllerTest {

    private GenericCrudService svc;
    private AuthService auth;
    private GenericCrudController ctrl;

    @BeforeEach
    void setUp() {
        svc = mock(GenericCrudService.class);
        auth = mock(AuthService.class);
        ctrl = new GenericCrudController(svc, auth);
        when(auth.effectiveUser(any())).thenReturn(new User());
        when(auth.getTableFilters(any())).thenReturn(Collections.emptyMap());
    }

    private MockHttpServletRequest req() { return new MockHttpServletRequest(); }

    @Test void unknownEntityReturns404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> ctrl.list("not-a-real-entity", null, 0, 50, null, null, req()));
        assertEquals(404, ex.getStatus().value());
        verifyNoInteractions(svc);
    }

    @Test void unsupportedOperationReturns403() {
        // process-status registers only SELECT/INSERT/UPDATE — DELETE is not allowed
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> ctrl.delete("process-status", "5", req()));
        assertEquals(403, ex.getStatus().value());
        verify(svc, never()).delete(any(), any(), any());
    }

    @Test void missingPrivilegeIsDenied() {
        doThrow(new SecurityException("no INSERT")).when(auth).requirePrivilege(any(), eq("INSERT"));
        assertThrows(SecurityException.class,
            () -> ctrl.create("donation-project", new HashMap<>(), req()));
        verify(svc, never()).create(any(), any());
    }

    @Test void missingTableAccessIsDenied() {
        doThrow(new SecurityException("no table access")).when(auth).requireTableAccess(any(), anyString());
        assertThrows(SecurityException.class,
            () -> ctrl.list("donation-project", null, 0, 50, null, null, req()));
        verify(svc, never()).list(any(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test void happyPathDelegatesToServiceWithRowFilter() {
        Map<String, String> filters = new HashMap<>();
        filters.put("DA_DONATION_PROJECTS", "STATUS = 'A'");
        when(auth.getTableFilters(any())).thenReturn(filters);
        Map<String, Object> expected = new HashMap<>();
        expected.put("items", Collections.emptyList());
        when(svc.list(any(), any(), anyInt(), anyInt(), eq("STATUS = 'A'"), any(), any())).thenReturn(expected);

        Map<String, Object> result = ctrl.list("donation-project", null, 0, 50, null, null, req());

        assertSame(expected, result);
        verify(auth).requirePrivilege(any(), eq("SELECT"));
        verify(auth).requireTableAccess(any(), eq("DA_DONATION_PROJECTS"));
        verify(svc).list(any(), isNull(), eq(0), eq(50), eq("STATUS = 'A'"), isNull(), isNull());
    }

    @Test void pageSizeIsClampedToMax500() {
        ctrl.list("donation-project", null, 0, 9999, null, null, req());
        verify(svc).list(any(), any(), eq(0), eq(500), any(), any(), any());
    }

    @Test void negativePageIsClampedToZero() {
        ctrl.list("donation-project", null, -3, 50, null, null, req());
        verify(svc).list(any(), any(), eq(0), eq(50), any(), any(), any());
    }
}
