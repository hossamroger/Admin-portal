package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fire-and-forget audit logging.
 *
 * Request threads only call {@link #record} which does a non-blocking offer() onto a
 * bounded in-memory queue — an O(1) operation that NEVER blocks. A single daemon thread
 * drains the queue and writes rows in batches. If the queue is full (e.g. the DB is slow),
 * new events are dropped rather than slowing any request down. This guarantees auditing
 * adds effectively zero latency to user requests, no matter the load.
 */
@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;
    private final AppProperties.Audit cfg;
    private final BlockingQueue<Event> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final String insertSql;
    private volatile boolean running = true;
    private volatile boolean tableReady = false;

    public AuditService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.cfg = props.getAudit();
        this.queue = new ArrayBlockingQueue<>(Math.max(100, cfg.getQueueCapacity()));
        String t = sanitize(cfg.getTable());
        this.insertSql = "INSERT INTO " + t +
            " (username, action, target, detail, success, http_status, client_ip, duration_ms, event_time)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        if (!cfg.isEnabled()) return;

        prepareTable(t);

        Thread worker = new Thread(this::drainLoop, "audit-writer");
        worker.setDaemon(true);
        worker.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "audit-flush"));
    }

    /** Cheap, non-blocking. Called on the request thread. */
    public void record(String username, String action, String target, String detail,
                       boolean success, int httpStatus, String clientIp, long durationMs) {
        if (!cfg.isEnabled() || !running) return;
        Event e = new Event();
        e.username = trim(username, 128);
        e.action = trim(action, 64);
        e.target = trim(target, 256);
        e.detail = trim(detail, 4000);
        e.success = success;
        e.httpStatus = httpStatus;
        e.clientIp = trim(clientIp, 64);
        e.durationMs = durationMs;
        e.eventTime = Instant.now();
        if (!queue.offer(e)) {                 // full → drop, never block the request
            LOG.warn("Audit queue full — event dropped (total dropped: {})", dropped.incrementAndGet());
        }
    }

    public long getDroppedCount() { return dropped.get(); }

    /** Current number of buffered events awaiting the background writer. */
    public int getQueueSize() { return queue.size(); }

    // ---- background writer ----

    private void drainLoop() {
        List<Event> batch = new ArrayList<>(cfg.getBatchSize());
        while (running || !queue.isEmpty()) {
            try {
                Event first = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (first == null) continue;
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, cfg.getBatchSize() - 1);
                flush(batch);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                // never let the writer die; back off briefly so we don't spin on a DB error
                LOG.warn("Audit batch write failed: {}", ex.getMessage());
                sleep(2000);
            }
        }
    }

    private void flush(List<Event> batch) {
        if (batch.isEmpty() || !tableReady) return;
        List<Object[]> args = new ArrayList<>(batch.size());
        for (Event e : batch) {
            args.add(new Object[]{
                e.username, e.action, e.target, e.detail,
                e.success ? "Y" : "N", e.httpStatus, e.clientIp, e.durationMs,
                Timestamp.from(e.eventTime)
            });
        }
        jdbc.batchUpdate(insertSql, args);
    }

    private void prepareTable(String t) {
        try {
            Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = ?", Integer.class, t);
            if (n != null && n > 0) { tableReady = true; return; }
            if (!cfg.isAutoCreateTable()) {
                LOG.warn("Audit table {} not found and auto-create is off; auditing disabled.", t);
                return;
            }
            jdbc.execute(
                "CREATE TABLE " + t + " (" +
                "  id           NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                "  username     VARCHAR2(128), " +
                "  action       VARCHAR2(64), " +
                "  target       VARCHAR2(256), " +
                "  detail       VARCHAR2(4000), " +
                "  success      CHAR(1), " +
                "  http_status  NUMBER(5), " +
                "  client_ip    VARCHAR2(64), " +
                "  duration_ms  NUMBER(12), " +
                "  event_time   TIMESTAMP" +
                ")");
            jdbc.execute("CREATE INDEX " + t + "_TIME_IX ON " + t + " (event_time)");
            tableReady = true;
            LOG.info("Created audit table {}", t);
        } catch (Exception ex) {
            // best-effort: if we can't see/create the table, disable writes but keep the app running
            LOG.warn("Could not prepare audit table {} ({}). Auditing will be skipped.", t, ex.getMessage());
            tableReady = false;
        }
    }

    private void shutdown() {
        running = false;
        // final drain so buffered events aren't lost on a clean stop
        List<Event> rest = new ArrayList<>();
        queue.drainTo(rest);
        try { flush(rest); } catch (Exception ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sanitize(String name) {
        if (name == null || !name.toUpperCase().matches("[A-Z0-9_$#]+"))
            throw new IllegalArgumentException("Invalid audit table name: " + name);
        return name.toUpperCase();
    }

    /** A single audit record. */
    private static class Event {
        String username, action, target, detail, clientIp;
        boolean success;
        int httpStatus;
        long durationMs;
        Instant eventTime;
    }
}
