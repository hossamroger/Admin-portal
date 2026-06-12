package com.example.dbexplorer.config;

import com.example.dbexplorer.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Exposes audit-pipeline internals as Micrometer gauges so the dropped-event
 * counter and the in-memory queue depth show up under /api/actuator/metrics
 * (audit.events.dropped, audit.queue.size) for monitoring.
 *
 * In addition to the gauges, a cheap periodic check emits a WARN whenever the
 * dropped counter increases since the last poll, so dropped audit events are
 * visible in the logs (where they're more likely to be noticed/alerted on)
 * without changing the queue/drop behavior itself.
 */
@Configuration
@EnableScheduling
public class ObservabilityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityConfig.class);

    private final AuditService audit;
    private long lastDropped = 0L;

    @Autowired
    public ObservabilityConfig(AuditService audit) {
        this.audit = audit;
    }

    @Autowired
    public void bindAuditMetrics(MeterRegistry registry, AuditService audit) {
        registry.gauge("audit.events.dropped", audit, AuditService::getDroppedCount);
        registry.gauge("audit.queue.size", audit, AuditService::getQueueSize);
    }

    /**
     * Non-blocking: just reads two atomics. If the dropped counter grew since the
     * last check, log how many events were lost in the interval. Returns the delta
     * so the behavior is easy to unit-test.
     */
    @Scheduled(fixedDelayString = "${dbexplorer.audit.drop-log-interval-ms:60000}")
    public long reportDroppedAuditEvents() {
        long current = audit.getDroppedCount();
        long delta = current - lastDropped;
        if (delta > 0) {
            LOG.warn("Audit events dropped: {} in the last interval (total dropped: {}, queue size: {})",
                delta, current, audit.getQueueSize());
        }
        lastDropped = current;
        return delta;
    }
}
