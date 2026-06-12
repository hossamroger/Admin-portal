package com.example.dbexplorer.config;

import com.example.dbexplorer.service.AuditService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservabilityConfigTest {

    @Test
    void reportsDeltaOnlyWhenDroppedCountIncreases() {
        AuditService audit = mock(AuditService.class);
        when(audit.getDroppedCount()).thenReturn(0L, 0L, 5L, 5L);
        when(audit.getQueueSize()).thenReturn(0);

        ObservabilityConfig cfg = new ObservabilityConfig(audit);

        // first poll: no drops yet
        assertEquals(0L, cfg.reportDroppedAuditEvents());
        // second poll: still 0
        assertEquals(0L, cfg.reportDroppedAuditEvents());
        // third poll: jumped to 5 -> delta 5
        assertEquals(5L, cfg.reportDroppedAuditEvents());
        // fourth poll: still 5 -> delta 0
        assertEquals(0L, cfg.reportDroppedAuditEvents());
    }
}
