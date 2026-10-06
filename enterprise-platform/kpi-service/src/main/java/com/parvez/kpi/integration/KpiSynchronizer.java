package com.parvez.kpi.integration;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class KpiSynchronizer {
    private final JdbcTemplate jdbc;
    private final TaskClient client;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    private final boolean enabled;

    public KpiSynchronizer(JdbcTemplate jdbc, TaskClient client, PlatformTransactionManager manager, MeterRegistry metrics, @Value("${kpi.sync-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.client = client;
        this.tx = new TransactionTemplate(manager);
        this.metrics = metrics;
        this.enabled = enabled;
        metrics.counter("kpi.sync.success");
        metrics.counter("kpi.sync.failure");
        io.micrometer.core.instrument.Gauge.builder("kpi.sync.age.seconds", jdbc, j -> j.queryForObject("SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-last_successful_sync_at)), -1) FROM kpi_sync_state WHERE id=1", Double.class)).register(metrics);
    }

    @Scheduled(fixedDelayString = "${kpi.sync-interval-ms:60000}", initialDelayString = "${kpi.sync-initial-delay-ms:1000}")
    public void scheduled() {
        if (enabled) refresh();
    }

    public boolean refresh() {
        UUID generation = UUID.randomUUID();
        Instant start = Instant.now();
        if (jdbc.update("UPDATE kpi_sync_state SET lease_owner=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '330 seconds' WHERE id=1 AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)", generation) != 1)
            return false;
        var previous = MDC.getCopyOfContextMap();
        String id = UUID.randomUUID().toString();
        MDC.put("requestId", id);
        MDC.put("traceId", id);
        try {
            UUID cursor = null, upper = null;
            boolean finished = false;
            for (int count = 0; count < 10000 && Duration.between(start, Instant.now()).compareTo(Duration.ofMinutes(5)) < 0; count++) {
                TaskClient.Page page = client.page(cursor, upper);
                if (page == null || page.content() == null || page.content().size() > 500)
                    throw new IllegalStateException("Invalid metric page");
                if (upper != null && !upper.equals(page.upperId()))
                    throw new IllegalStateException("Unstable metric boundary");
                UUID previousId = cursor;
                for (var row : page.content()) {
                    if (row.id() == null || row.createdAt() == null || row.teamId() == null || row.projectId() == null || (previousId != null && row.id().toString().compareTo(previousId.toString()) <= 0) || page.upperId() == null || row.id().toString().compareTo(page.upperId().toString()) > 0)
                        throw new IllegalStateException("Invalid metric ordering");
                    previousId = row.id();
                }
                if (page.nextCursor() != null && (page.content().isEmpty() || !page.nextCursor().equals(page.content().getLast().id())))
                    throw new IllegalStateException("Invalid cursor");
                tx.executeWithoutResult(status -> {
                    for (var row : page.content())
                        jdbc.update("INSERT INTO metric_tasks(generation,task_id,version,employee_id,team_id,project_id,status,priority,due_date,created_at,started_at,completed_at,closed_at,creation_request_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                generation, row.id(), row.version(), row.employeeId(), row.teamId(), row.projectId(), row.status(), row.priority(), row.dueDate(), timestamp(row.createdAt()), timestamp(row.startedAt()), timestamp(row.completedAt()), timestamp(row.closedAt()), validId(row.creationRequestId()));
                });
                for (var row : page.content()) {
                    String original = validId(row.creationRequestId());
                    if (original != null) {
                        MDC.put("requestId", original);
                        MDC.put("traceId", original);
                        LoggerFactory.getLogger(KpiSynchronizer.class).info("task_metric_staged syncId={}", id);
                        MDC.put("requestId", id);
                        MDC.put("traceId", id);
                    }
                }
                upper = page.upperId();
                cursor = page.nextCursor();
                if (cursor == null) {
                    finished = true;
                    break;
                }
            }
            if (!finished) throw new IllegalStateException("Metric sweep exceeded its limit");
            int published = jdbc.update("UPDATE kpi_sync_state SET active_generation=?,data_as_of=?,last_successful_sync_at=CURRENT_TIMESTAMP,lease_owner=NULL,lease_until=NULL WHERE id=1 AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP", generation, Timestamp.from(start), generation);
            if (published != 1) throw new IllegalStateException("Metric lease expired");
            metrics.counter("kpi.sync.success").increment();
            return true;
        } catch (RuntimeException error) {
            metrics.counter("kpi.sync.failure").increment();
            LoggerFactory.getLogger(KpiSynchronizer.class).warn("metric_refresh_failed");
            return false;
        } finally {
            try {
                jdbc.update("UPDATE kpi_sync_state SET lease_owner=NULL,lease_until=NULL WHERE id=1 AND lease_owner=?", generation);
                cleanup();
            } catch (RuntimeException failure) {
                LoggerFactory.getLogger(KpiSynchronizer.class).warn("metric_cleanup_failed");
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void cleanup() {
        jdbc.update("DELETE FROM metric_tasks WHERE (generation,task_id) IN (SELECT generation,task_id FROM metric_tasks WHERE generation NOT IN (SELECT active_generation FROM kpi_sync_state WHERE active_generation IS NOT NULL UNION SELECT lease_owner FROM kpi_sync_state WHERE lease_owner IS NOT NULL) LIMIT 500)");
    }

    private static String validId(String value) {
        return value != null && value.matches("[a-zA-Z0-9-]{1,64}") ? value : null;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
