package com.example.dbexplorer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubResourceServiceTest {

    private JdbcTemplate jdbc;
    private SubResourceService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        svc = new SubResourceService(jdbc);
    }

    private static Map<String, Object> row(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    private SubResourceService.SubResourceSpec specWithLiteral() {
        LinkedHashMap<String, String> literals = new LinkedHashMap<>();
        literals.put("CREATED", "SYSDATE");
        return new SubResourceService.SubResourceSpec(
            "CHILD_T", "PARENT_ID", "ID",
            null,
            (input, id, order, parentKey) -> {
                LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                m.put("ID", id);
                m.put("PARENT_ID", parentKey);
                m.put("NAME", input.get("NAME"));
                m.put("ORD", order);
                return m;
            },
            literals);
    }

    @Test
    void deletesThenInsertsWithMaxPlusOrderAndLiteralColumn() {
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(ID),0)"), eq(Long.class))).thenReturn(40L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.replaceAll(specWithLiteral(), 7L, Arrays.asList(row("NAME", "a"), row("NAME", "b")));

        InOrder inOrder = inOrder(jdbc);
        inOrder.verify(jdbc).update(eq("DELETE FROM CHILD_T WHERE PARENT_ID = ?"), ArgumentMatchers.<Object[]>any());

        // ids = base+order (41, 42); order 1-based; literal CREATED is not a bind arg
        verify(jdbc).update(startsWith("INSERT INTO CHILD_T"), eq(41L), eq(7L), eq("a"), eq(1));
        verify(jdbc).update(startsWith("INSERT INTO CHILD_T"), eq(42L), eq(7L), eq("b"), eq(2));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).update(sql.capture(), ArgumentMatchers.<Object[]>any());
        // the literal column appears in the INSERT text with SYSDATE, not a placeholder
        assertTrue(sql.getAllValues().get(1).contains("CREATED"));
        assertTrue(sql.getAllValues().get(1).contains("SYSDATE"));
    }

    @Test
    void validatorRunsBeforeAnyWrite() {
        SubResourceService.SubResourceSpec spec = new SubResourceService.SubResourceSpec(
            "CHILD_T", "PARENT_ID", "ID",
            rows -> { throw new IllegalArgumentException("bad payload"); },
            (input, id, order, parentKey) -> new LinkedHashMap<>(),
            null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.replaceAll(spec, 7L, Collections.singletonList(row("NAME", "a"))));
        assertEquals("bad payload", ex.getMessage());
        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void emptyRowsStillDeletesParentChildren() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        svc.replaceAll(specWithLiteral(), 5L, Collections.emptyList());
        verify(jdbc, times(1)).update(eq("DELETE FROM CHILD_T WHERE PARENT_ID = ?"), ArgumentMatchers.<Object[]>any());
        verify(jdbc, never()).update(startsWith("INSERT"), ArgumentMatchers.<Object[]>any());
    }
}
