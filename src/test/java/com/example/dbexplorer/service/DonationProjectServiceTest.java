package com.example.dbexplorer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DonationProjectServiceTest {

    private JdbcTemplate jdbc;
    private DonationProjectService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        svc = new DonationProjectService(jdbc);
    }

    private static Map<String, Object> row(String key, Object val) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, val);
        return m;
    }

    // ── saveAmounts ───────────────────────────────────────────────────────────

    @Test
    void saveAmountsDeletesThenInsertsWithSequentialMaxPlusOneIds() {
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(ID),0)+1"), eq(Long.class)))
            .thenReturn(5L, 6L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.saveAmounts(9L, Arrays.asList(row("AMOUNT", "100"), row("AMOUNT", 250)));

        InOrder inOrder = inOrder(jdbc);
        inOrder.verify(jdbc).update(
            eq("DELETE FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ?"),
            ArgumentMatchers.<Object[]>any());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        // first call is the delete, then two inserts
        verify(jdbc, times(3)).update(sql.capture(), ArgumentMatchers.<Object[]>any());
        assertTrue(sql.getAllValues().get(1).startsWith("INSERT INTO DA_DONATION_PROJECTS_AMOUNT"));
        assertTrue(sql.getAllValues().get(2).startsWith("INSERT INTO DA_DONATION_PROJECTS_AMOUNT"));

        // sequential MAX+1 ids: bound args of the two insert calls
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_AMOUNT"),
            eq(5L), eq(9L), eq(new BigDecimal("100")));
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_AMOUNT"),
            eq(6L), eq(9L), eq(new BigDecimal("250")));
    }

    @Test
    void saveAmountsSkipsNullAmounts() {
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", null)));

        // only the delete happens, no insert and no MAX+1 query
        verify(jdbc, times(1)).update(anyString(), ArgumentMatchers.<Object[]>any());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void saveAmountsRejectsNonNumeric() {
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        assertThrows(IllegalArgumentException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", "abc"))));
        // delete already happened before the validation failure (no transaction!)
        verify(jdbc).update(eq("DELETE FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ?"),
            ArgumentMatchers.<Object[]>any());
    }

    // ── saveDetails ───────────────────────────────────────────────────────────

    @Test
    void saveDetailsDeletesThenInsertsWithOrderOneToN() {
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(REC_ID),0)+1"), eq(Long.class)))
            .thenReturn(11L, 12L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        Map<String, Object> d1 = new HashMap<>();
        d1.put("LABEL_AR", "ع1"); d1.put("LABEL_EN", "e1");
        d1.put("DESC_AR", "دع1"); d1.put("DESC_EN", "de1");
        Map<String, Object> d2 = new HashMap<>();
        d2.put("LABEL_EN", "e2");

        svc.saveDetails(7L, Arrays.asList(d1, d2));

        InOrder inOrder = inOrder(jdbc);
        inOrder.verify(jdbc).update(
            eq("DELETE FROM DA_DONATION_PROJECTS_DETAILS WHERE PRJ_ID = ?"),
            ArgumentMatchers.<Object[]>any());

        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_DETAILS"),
            eq(11L), eq(7L), eq("ع1"), eq("e1"), eq("دع1"), eq("de1"), eq(1));
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_DETAILS"),
            eq(12L), eq(7L), isNull(), eq("e2"), isNull(), isNull(), eq(2));
    }

    // ── list delegation ───────────────────────────────────────────────────────

    @Test
    void listAmountsDelegatesCorrectSql() {
        List<Map<String, Object>> rows = Collections.singletonList(row("AMOUNT", 5));
        when(jdbc.queryForList(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(rows);

        assertSame(rows, svc.listAmounts(3L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(3L));
        assertEquals(
            "SELECT ID, AMOUNT FROM DA_DONATION_PROJECTS_AMOUNT WHERE PROJECT_ID = ? ORDER BY AMOUNT",
            sql.getValue());
    }

    @Test
    void listDetailsDelegatesCorrectSql() {
        List<Map<String, Object>> rows = Collections.singletonList(row("LABEL_EN", "x"));
        when(jdbc.queryForList(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(rows);

        assertSame(rows, svc.listDetails(3L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(3L));
        assertEquals(
            "SELECT REC_ID, LABEL_AR, LABEL_EN, DESC_AR, DESC_EN, ORDER_C " +
            "FROM DA_DONATION_PROJECTS_DETAILS WHERE PRJ_ID = ? ORDER BY ORDER_C NULLS LAST, REC_ID",
            sql.getValue());
    }
}
