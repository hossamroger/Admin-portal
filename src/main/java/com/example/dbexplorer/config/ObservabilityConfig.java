package com.example.dbexplorer.config;

import com.example.dbexplorer.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes audit-pipeline internals as Micrometer gauges so the dropped-event
 * counter and the in-memory queue depth show up under /api/actuator/metrics
 * (audit.events.dropped, audit.queue.size) for monitoring.
 */
@Configuration
public class ObservabilityConfig {

    @Autowired
    public void bindAuditMetrics(MeterRegistry registry, AuditService audit) {
        registry.gauge("audit.events.dropped", audit, AuditService::getDroppedCount);
        registry.gauge("audit.queue.size", audit, AuditService::getQueueSize);
    }
}
