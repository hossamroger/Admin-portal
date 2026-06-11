package com.example.dbexplorer.service;

import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Types;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenericCrudServiceDeleteTest {

    private JdbcTemplate jdbc;
    private GenericCrudService svc;
    private CrudEntity entity;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        svc = new GenericCrudService(jdbc);

        Map<String, GenericCrudService.ColMeta> meta = new LinkedHashMap<>();
        meta.put("ID",   new GenericCrudService.ColMeta(Types.NUMERIC, false, 10));
        meta.put("NAME", new GenericCrudService.ColMeta(Types.VARCHAR, false, 50));
        when(jdbc.query(startsWith("SELECT * FROM T WHERE 1 = 0"), any(ResultSetExtractor.class)))
            .thenReturn(meta);

        entity = new CrudEntity("t", "T", "ID");
    }

    @Test
    void deleteHappyPathBindsCoercedPk() {
        // varargs: single pk arg
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.delete(entity, "42", null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> arg = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(sql.capture(), arg.capture());
        assertEquals("DELETE FROM T WHERE ID = ?", sql.getValue());
        // pk is a NUMERIC column, so the string id is coerced to BigDecimal
        assertEquals(new java.math.BigDecimal("42"), arg.getValue());
    }

    @Test
    void deleteZeroRowsThrowsNotFound() {
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(0);
        assertThrows(NoSuchElementException.class, () -> svc.delete(entity, "99", null));
    }

    @Test
    void deleteAppliesRowFilter() {
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.delete(entity, "7", "TENANT_ID = 3");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), ArgumentMatchers.<Object[]>any());
        assertEquals("DELETE FROM T WHERE ID = ? AND (TENANT_ID = 3)", sql.getValue());
    }
}
