package com.example.dbexplorer.service;

import com.example.dbexplorer.config.CrudEntities.CrudEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Types;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrudHookTest {

    private JdbcTemplate jdbc;
    private CrudEntity entity;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        Map<String, GenericCrudService.ColMeta> meta = new LinkedHashMap<>();
        meta.put("ID",   new GenericCrudService.ColMeta(Types.NUMERIC, false, 10));
        meta.put("NAME", new GenericCrudService.ColMeta(Types.VARCHAR, false, 50));
        when(jdbc.query(startsWith("SELECT * FROM T WHERE 1 = 0"), any(ResultSetExtractor.class))).thenReturn(meta);
        entity = new CrudEntity("t", "T", "ID");
    }

    /** Records the callbacks it received, in order. */
    private static final class RecordingHook implements CrudHook {
        final List<String> calls = new ArrayList<>();
        @Override public String entity() { return "t"; }
        @Override public void beforeInsert(CrudEntity e, Map<String, Object> row) { calls.add("beforeInsert"); }
        @Override public void afterInsert(CrudEntity e, Map<String, Object> row, Object id) { calls.add("afterInsert:" + id); }
        @Override public void beforeUpdate(CrudEntity e, String id, Map<String, Object> row) { calls.add("beforeUpdate:" + id); }
        @Override public void beforeDelete(CrudEntity e, String id) { calls.add("beforeDelete:" + id); }
    }

    @Test
    void createInvokesBeforeAndAfterInsertWithId() {
        RecordingHook hook = new RecordingHook();
        GenericCrudService svc = new GenericCrudService(jdbc, Collections.singletonList(hook));
        // nextValue returns the queried value directly (MAX+1 is computed in SQL)
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX("), eq(Long.class))).thenReturn(41L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        Object id = svc.create(entity, Collections.singletonMap("NAME", "x"));

        assertEquals(41L, id);
        assertEquals(Arrays.asList("beforeInsert", "afterInsert:41"), hook.calls);
    }

    @Test
    void updateInvokesBeforeUpdate() {
        RecordingHook hook = new RecordingHook();
        GenericCrudService svc = new GenericCrudService(jdbc, Collections.singletonList(hook));
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.update(entity, "7", Collections.singletonMap("NAME", "y"), null);

        assertEquals(Collections.singletonList("beforeUpdate:7"), hook.calls);
    }

    @Test
    void deleteInvokesBeforeDelete() {
        RecordingHook hook = new RecordingHook();
        GenericCrudService svc = new GenericCrudService(jdbc, Collections.singletonList(hook));
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.delete(entity, "9", null);

        assertEquals(Collections.singletonList("beforeDelete:9"), hook.calls);
    }

    @Test
    void noHookRegisteredIsANoOp() {
        GenericCrudService svc = new GenericCrudService(jdbc, Collections.emptyList());
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX("), eq(Long.class))).thenReturn(1L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);
        // entity "t" has no hook → create proceeds normally
        assertEquals(1L, svc.create(entity, Collections.singletonMap("NAME", "x")));
    }

    @Test
    void hookOnlyAppliesToItsOwnEntity() {
        RecordingHook hook = new RecordingHook(); // entity() == "t"
        GenericCrudService svc = new GenericCrudService(jdbc, Collections.singletonList(hook));
        CrudEntity other = new CrudEntity("other", "OTHER", "ID");
        when(jdbc.query(startsWith("SELECT * FROM OTHER WHERE 1 = 0"), any(ResultSetExtractor.class)))
            .thenReturn(Collections.singletonMap("ID", new GenericCrudService.ColMeta(Types.NUMERIC, false, 10)));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);

        svc.create(other, Collections.singletonMap("ID", "1"));
        assertTrue(hook.calls.isEmpty(), "hook for 't' must not fire for 'other'");
    }
}
