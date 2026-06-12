package com.example.dbexplorer.service;

import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.*;

class GenericCrudServiceTest {

    private JdbcTemplate jdbc;
    private GenericCrudService svc;
    private CrudEntity entity;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        svc = new GenericCrudService(jdbc);

        // metadata discovery query returns a fixed column map
        Map<String, GenericCrudService.ColMeta> meta = new LinkedHashMap<>();
        meta.put("ID",        new GenericCrudService.ColMeta(Types.NUMERIC, false, 10));
        meta.put("NAME",      new GenericCrudService.ColMeta(Types.VARCHAR, false, 5));
        meta.put("NOTE",      new GenericCrudService.ColMeta(Types.VARCHAR, true, 100));
        meta.put("WHEN_AT",   new GenericCrudService.ColMeta(Types.TIMESTAMP, true, 0));
        meta.put("SECRET",    new GenericCrudService.ColMeta(Types.VARCHAR, true, 100));
        when(jdbc.query(startsWith("SELECT * FROM T WHERE 1 = 0"), any(ResultSetExtractor.class)))
            .thenReturn(meta);

        entity = new CrudEntity("t", "T", "ID");
        entity.searchCols = Arrays.asList("NAME");
    }

    // ── parseTemporal: strict, non-lenient ────────────────────────────────────

    @Test
    void parsesSupportedPatterns() {
        assertNotNull(GenericCrudService.parseTemporal("C", "2026-06-11T10:30:00"));
        assertNotNull(GenericCrudService.parseTemporal("C", "2026-06-11T10:30"));
        assertNotNull(GenericCrudService.parseTemporal("C", "2026-06-11 10:30:00"));
        assertNotNull(GenericCrudService.parseTemporal("C", "2026-06-11"));
    }

    @Test
    void rejectsGarbageDates() {
        assertThrows(IllegalArgumentException.class,
            () -> GenericCrudService.parseTemporal("C", "not-a-date"));
    }

    @Test
    void rejectsRolledOverDates() {
        // lenient parsing would silently turn this into 2026-03-03
        assertThrows(IllegalArgumentException.class,
            () -> GenericCrudService.parseTemporal("C", "2026-02-31"));
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThrows(IllegalArgumentException.class,
            () -> GenericCrudService.parseTemporal("C", "2026-01-01garbage"));
    }

    // ── coerce ────────────────────────────────────────────────────────────────

    @Test
    void coercesNumbersAndRejectsBadInput() {
        assertEquals(new java.math.BigDecimal("42"), svc.coerce("T", "ID", "42"));
        assertThrows(IllegalArgumentException.class, () -> svc.coerce("T", "ID", "abc"));
    }

    @Test
    void coercesEmptyToNullAndDates() {
        assertNull(svc.coerce("T", "NOTE", "   "));
        assertTrue(svc.coerce("T", "WHEN_AT", "2026-06-11") instanceof Timestamp);
    }

    // ── list: wildcard escaping + row filter ──────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void searchEscapesLikeWildcardsAndAppliesRowFilter() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(Collections.emptyList());

        svc.list(entity, "50%_x", 0, 50, "TENANT_ID = 7", null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> arg = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), arg.capture());

        assertTrue(sql.getValue().contains("ESCAPE '\\'"));
        assertTrue(sql.getValue().contains("(TENANT_ID = 7)"));
        assertEquals("%50\\%\\_X%", arg.getAllValues().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsUnknownSortColumn() {
        assertThrows(IllegalArgumentException.class,
            () -> svc.list(entity, null, 0, 50, null, "EVIL; DROP", "ASC"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsKnownSortColumn() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(Collections.emptyList());

        svc.list(entity, null, 0, 50, null, "name", "desc");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("ORDER BY NAME DESC, ID"));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void updateOfMissingRowThrowsNotFound() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        Map<String, Object> vals = new HashMap<>();
        vals.put("NOTE", "x");
        assertThrows(NoSuchElementException.class, () -> svc.update(entity, "1", vals, null));
    }

    @Test
    void updateRejectsBlankNotNullColumn() {
        Map<String, Object> vals = new HashMap<>();
        vals.put("NAME", "   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.update(entity, "1", vals, null));
        assertTrue(ex.getMessage().contains("NAME is required"));
    }

    @Test
    void updateRejectsOverlongValue() {
        Map<String, Object> vals = new HashMap<>();
        vals.put("NAME", "toolongvalue"); // column size is 5
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.update(entity, "1", vals, null));
        assertTrue(ex.getMessage().contains("maximum length"));
    }

    @Test
    void writableColsBlocksMassAssignment() {
        entity.writableCols = new HashSet<>(Arrays.asList("NAME", "NOTE"));
        // varargs: NAME value + pk
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1); // any() matches all varargs

        Map<String, Object> vals = new HashMap<>();
        vals.put("NAME", "ok");
        vals.put("SECRET", "hax"); // real column, but not exposed by the entity
        svc.update(entity, "1", vals, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), ArgumentMatchers.<Object[]>any());
        assertTrue(sql.getValue().contains("NAME = ?"));
        assertFalse(sql.getValue().contains("SECRET"));
    }

    @Test
    void createRejectsAbsentNotNullColumn() {
        // NAME is NOT NULL; leaving it out of the payload entirely must be a 400,
        // not a raw DB constraint violation (500)
        Map<String, Object> vals = new HashMap<>();
        vals.put("NOTE", "x");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> svc.create(entity, vals));
        assertTrue(ex.getMessage().contains("NAME is required"));
    }

    @Test
    void updateAllowsAbsentNotNullColumn() {
        // Partial updates may omit NOT NULL columns — only INSERT requires them
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);
        Map<String, Object> vals = new HashMap<>();
        vals.put("NOTE", "x");
        svc.update(entity, "1", vals, null); // must not throw
    }

    // ── create: retry on duplicate key ────────────────────────────────────────

    @Test
    void createRetriesOnDuplicateKey() {
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX("), eq(Long.class)))
            .thenReturn(10L, 11L);
        // varargs: NAME value + generated ID
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any()))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"))
            .thenReturn(1);

        Map<String, Object> vals = new HashMap<>();
        vals.put("NAME", "ok");
        Object id = svc.create(entity, vals);
        assertEquals(11L, id);
        verify(jdbc, times(2)).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    // ── create: sequence-aware allocation ─────────────────────────────────────

    @Test
    void createUsesSequenceNextvalWhenSequenceExists() {
        // probe finds T_SEQ
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM user_sequences WHERE sequence_name = ?"),
                eq(Integer.class), eq("T_SEQ"))).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT T_SEQ.NEXTVAL"), eq(Long.class))).thenReturn(100L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        Map<String, Object> vals = new HashMap<>();
        vals.put("NAME", "ok");
        Object id = svc.create(entity, vals);

        assertEquals(100L, id);
        // sequence path is atomic — must not fall back to MAX+1
        verify(jdbc, never()).queryForObject(startsWith("SELECT NVL(MAX("), eq(Long.class));
        verify(jdbc, times(1)).update(anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void createFallsBackToMaxPlusOneWhenNoSequence() {
        when(jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM user_sequences WHERE sequence_name = ?"),
                eq(Integer.class), eq("T_SEQ"))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX("), eq(Long.class))).thenReturn(5L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        Map<String, Object> vals = new HashMap<>();
        vals.put("NAME", "ok");
        Object id = svc.create(entity, vals);

        assertEquals(5L, id);
        verify(jdbc, never()).queryForObject(startsWith("SELECT T_SEQ.NEXTVAL"), eq(Long.class));
    }
}
