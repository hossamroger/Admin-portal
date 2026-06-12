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
        // the sub-resource engine shares the same JdbcTemplate mock, so all the
        // existing verify(jdbc) assertions still observe the delete/insert calls
        svc = new DonationProjectService(jdbc, new SubResourceService(jdbc));
    }

    private static Map<String, Object> row(String key, Object val) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, val);
        return m;
    }

    /** Stub the requireProject existence check. */
    private void projectExists(long projectId, boolean exists) {
        when(jdbc.queryForObject(
            eq("SELECT COUNT(*) FROM DA_DONATION_PROJECTS WHERE PRJ_ID = ?"),
            eq(Long.class), eq(projectId)))
            .thenReturn(exists ? 1L : 0L);
    }

    // ── saveAmounts ───────────────────────────────────────────────────────────

    @Test
    void saveAmountsValidatesThenDeletesThenInsertsWithBasePlusN() {
        projectExists(9L, true);
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(ID),0)"), eq(Long.class)))
            .thenReturn(4L);
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

        // single MAX query, ids are base+1..base+n
        verify(jdbc, times(1)).queryForObject(startsWith("SELECT NVL(MAX(ID),0)"), eq(Long.class));
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_AMOUNT"),
            eq(5L), eq(9L), eq(new BigDecimal("100")));
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_AMOUNT"),
            eq(6L), eq(9L), eq(new BigDecimal("250")));
    }

    @Test
    void saveAmountsRejectsNonNumericBeforeDelete() {
        projectExists(9L, true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", "abc"))));
        assertEquals("Invalid amount: abc", ex.getMessage());

        // validation fails before any row is touched — no delete, no insert
        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void saveAmountsRejectsNullOrBlankAmount() {
        projectExists(9L, true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", null))));
        assertEquals("Amount is required", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", "  "))));
        assertEquals("Amount is required", ex.getMessage());

        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void saveAmountsRejectsNonPositiveAmount() {
        projectExists(9L, true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", "-5"))));
        assertEquals("Amount must be greater than zero", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", "0"))));
        assertEquals("Amount must be greater than zero", ex.getMessage());

        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void saveAmountsThrowsForUnknownProject() {
        projectExists(9L, false);

        assertThrows(NoSuchElementException.class,
            () -> svc.saveAmounts(9L, Collections.singletonList(row("AMOUNT", "100"))));
        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    // ── saveDetails ───────────────────────────────────────────────────────────

    @Test
    void saveDetailsDeletesThenInsertsWithOrderOneToN() {
        projectExists(7L, true);
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(REC_ID),0)"), eq(Long.class)))
            .thenReturn(10L);
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

        // single MAX query, REC_IDs are base+1..base+n
        verify(jdbc, times(1)).queryForObject(startsWith("SELECT NVL(MAX(REC_ID),0)"), eq(Long.class));
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_DETAILS"),
            eq(11L), eq(7L), eq("ع1"), eq("e1"), eq("دع1"), eq("de1"), eq(1));
        verify(jdbc).update(startsWith("INSERT INTO DA_DONATION_PROJECTS_DETAILS"),
            eq(12L), eq(7L), isNull(), eq("e2"), isNull(), isNull(), eq(2));
    }

    @Test
    void saveDetailsRejectsOversizedLabelBeforeDelete() {
        projectExists(7L, true);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1601; i++) sb.append('x');

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.saveDetails(7L, Collections.singletonList(row("LABEL_AR", sb.toString()))));
        assertEquals("LABEL_AR exceeds maximum length of 1600", ex.getMessage());

        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void saveDetailsThrowsForUnknownProject() {
        projectExists(7L, false);

        assertThrows(NoSuchElementException.class,
            () -> svc.saveDetails(7L, Collections.emptyList()));
        verify(jdbc, never()).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    // ── list delegation ───────────────────────────────────────────────────────

    @Test
    void listAmountsDelegatesCorrectSql() {
        projectExists(3L, true);
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
    void listAmountsThrowsForUnknownProject() {
        projectExists(3L, false);

        NoSuchElementException ex = assertThrows(NoSuchElementException.class,
            () -> svc.listAmounts(3L));
        assertEquals("Project not found: 3", ex.getMessage());
        verify(jdbc, never()).queryForList(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void listDetailsDelegatesCorrectSql() {
        projectExists(3L, true);
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

    @Test
    void listDetailsThrowsForUnknownProject() {
        projectExists(3L, false);

        assertThrows(NoSuchElementException.class, () -> svc.listDetails(3L));
        verify(jdbc, never()).queryForList(anyString(), ArgumentMatchers.<Object[]>any());
    }
}
