package com.example.dbexplorer.service;

import com.example.dbexplorer.dto.ServiceConfigDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Characterization tests pinning the CURRENT ServiceConfigService write behavior
 * (cascade-delete order, UUID-vs-supplied sequence ids, per-row MAX+1 ids, nested
 * children, and delete ordering) so the upcoming SubResource-engine refactor can be
 * proven behavior-preserving. These assert the existing contract, not an ideal one.
 */
class ServiceConfigServiceTest {

    private JdbcTemplate jdbc;
    private ServiceConfigService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        // ServiceConfigService builds a NamedParameterJdbcTemplate from the DataSource
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        // the sub-resource engine shares the same JdbcTemplate mock, so the
        // delete/insert/MAX+1 assertions still observe every call
        svc = new ServiceConfigService(jdbc, new SubResourceService(jdbc));
        when(jdbc.update(anyString(), ArgumentMatchers.<Object[]>any())).thenReturn(1);
    }

    // ── Flat sub-resource: fees ─────────────────────────────────────────────────

    @Test
    void saveFeesDeletesByProcessCodeThenInsertsGeneratingUuidWhenSeqNull() {
        FeeDto f = new FeeDto();
        f.feesDescAr = "ع"; f.feesDescEn = "e"; f.orderC = 1;

        svc.saveFees("SVC1", Collections.singletonList(f));

        InOrder in = inOrder(jdbc);
        in.verify(jdbc).update(eq("DELETE FROM BPM_LKP_FEES WHERE PROCESS_CODE = ?"), ArgumentMatchers.<Object[]>any());

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("INSERT INTO BPM_LKP_FEES"), args.capture());
        List<Object> a = args.getAllValues();
        assertTrue(a.get(0) instanceof String, "generated seq should be a UUID string");
        assertFalse(((String) a.get(0)).isEmpty());
        assertEquals("SVC1", a.get(1));
        assertEquals("ع", a.get(2));
        assertEquals("e", a.get(3));
    }

    @Test
    void saveFeesUsesSuppliedSeqVerbatim() {
        FeeDto f = new FeeDto();
        f.bpmProcessesFeesSeq = "FIXED-SEQ";
        svc.saveFees("SVC1", Collections.singletonList(f));

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("INSERT INTO BPM_LKP_FEES"), args.capture());
        assertEquals("FIXED-SEQ", args.getAllValues().get(0));
    }

    @Test
    void saveFeesWithEmptyListDeletesButInsertsNothing() {
        svc.saveFees("SVC1", Collections.emptyList());
        verify(jdbc).update(eq("DELETE FROM BPM_LKP_FEES WHERE PROCESS_CODE = ?"), ArgumentMatchers.<Object[]>any());
        verify(jdbc, never()).update(startsWith("INSERT INTO BPM_LKP_FEES"), ArgumentMatchers.<Object[]>any());
    }

    // ── Nested: steps -> status links ───────────────────────────────────────────

    @Test
    void saveStepsCascadeDeletesLinksBeforeStepsThenInsertsStepAndNestedLink() {
        StepDto s = new StepDto();
        s.requiredStepId = "STEP-A";
        StepLinkDto link = new StepLinkDto();
        link.requiredStatusCode = "ST1";
        s.statusLinks = new ArrayList<>(Collections.singletonList(link));

        // nested link uses per-row MAX+1 when stepLinkId is null
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(STEP_LINK_ID),0)+1"), eq(Long.class))).thenReturn(7L);

        svc.saveSteps("SVC1", Collections.singletonList(s));

        InOrder in = inOrder(jdbc);
        // links deleted (subquery) before steps
        in.verify(jdbc).update(startsWith("DELETE FROM BPM_LKP_SERV_STEP_STATUS_LINKS"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(eq("DELETE FROM BPM_LKP_STEPS WHERE PROCESS_CODE = ?"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(startsWith("INSERT INTO BPM_LKP_STEPS"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(startsWith("INSERT INTO BPM_LKP_SERV_STEP_STATUS_LINKS"), ArgumentMatchers.<Object[]>any());

        // step seq generated as UUID; link id allocated via MAX+1
        ArgumentCaptor<Object> linkArgs = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("INSERT INTO BPM_LKP_SERV_STEP_STATUS_LINKS"), linkArgs.capture());
        assertEquals(7L, linkArgs.getAllValues().get(0));
        verify(jdbc).queryForObject(startsWith("SELECT NVL(MAX(STEP_LINK_ID),0)+1"), eq(Long.class));
    }

    // ── Nested: confirmation screens -> components ──────────────────────────────

    @Test
    void saveConfirmationCascadeDeletesComponentsThenScreensThenInsertsNested() {
        ConfirmationScreenConfigDto screen = new ConfirmationScreenConfigDto();
        ConfirmationComponentConfigDto comp = new ConfirmationComponentConfigDto();
        comp.dsConfirmationComponentInfoId = 99;
        screen.components = new ArrayList<>(Collections.singletonList(comp));

        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(DS_CONFIRMATION_ID),0)+1"), eq(Long.class))).thenReturn(3L);
        when(jdbc.queryForObject(startsWith("SELECT NVL(MAX(DS_CONFIRMATION_CONFIG_ID),0)+1"), eq(Long.class))).thenReturn(5L);

        svc.saveConfirmation("SVC1", Collections.singletonList(screen));

        InOrder in = inOrder(jdbc);
        in.verify(jdbc).update(startsWith("DELETE FROM DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(eq("DELETE FROM DS_CONFIRMATION_SCREEN_CONFIG WHERE SERVICE_CODE = ?"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(startsWith("INSERT INTO DS_CONFIRMATION_SCREEN_CONFIG"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(startsWith("INSERT INTO DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG"), ArgumentMatchers.<Object[]>any());

        // component row links back to the parent screen's allocated id (3L)
        ArgumentCaptor<Object> compArgs = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("INSERT INTO DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG"), compArgs.capture());
        List<Object> c = compArgs.getAllValues();
        assertEquals(5L, c.get(0));   // DS_CONFIRMATION_CONFIG_ID via MAX+1
        assertEquals(99, c.get(1));   // component info id passed through
        assertEquals(3L, c.get(2));   // FK to parent screen id
    }

    // ── delete() ordering ───────────────────────────────────────────────────────

    @Test
    void deleteRemovesChildrenBeforeProcessInfo() {
        svc.delete("SVC1");

        InOrder in = inOrder(jdbc);
        in.verify(jdbc).update(startsWith("DELETE FROM DS_CONFIRMATION_SCREEN_COMPONENTS_CONFIG"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(startsWith("DELETE FROM BPM_LKP_SERV_STEP_STATUS_LINKS"), ArgumentMatchers.<Object[]>any());
        // process info removed last
        in.verify(jdbc).update(eq("DELETE FROM BPM_PROCESSES_INFO WHERE PROCESS_CODE = ?"), ArgumentMatchers.<Object[]>any());
    }

    // ── create() inserts process info first ─────────────────────────────────────

    @Test
    void createInsertsProcessInfoBeforeChildren() {
        ServiceConfigRequest req = new ServiceConfigRequest();
        req.processInfo = new ProcessInfoDto();
        req.processInfo.processCode = "SVC1";
        FeeDto f = new FeeDto();
        req.fees = Collections.singletonList(f);

        svc.create(req);

        InOrder in = inOrder(jdbc);
        in.verify(jdbc).update(startsWith("INSERT INTO BPM_PROCESSES_INFO"), ArgumentMatchers.<Object[]>any());
        in.verify(jdbc).update(startsWith("INSERT INTO BPM_LKP_FEES"), ArgumentMatchers.<Object[]>any());
    }
}
