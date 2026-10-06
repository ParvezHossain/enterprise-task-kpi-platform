package com.parvez.task.service;
import java.sql.Timestamp;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
public class TaskAuditObserver {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    public TaskAuditObserver(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc=jdbc; this.metrics=metrics;
        for(String name: new String[]{"created","approved","assigned","started","completed","closed"}) metrics.counter("tasks."+name);
        io.micrometer.core.instrument.Gauge.builder("tasks.overdue",jdbc,j -> j.queryForObject("SELECT count(*) FROM tasks WHERE due_date<CURRENT_DATE AND status NOT IN ('COMPLETED','CLOSED')",Long.class)).register(metrics);
    }
    @EventListener
    public void onTransition(TaskAuditEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Audit requires the task transaction");
        jdbc.update("INSERT INTO task_audit_log(id,task_id,actor_id,action,previous_status,new_status,occurred_at,metadata) VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),event.taskId(),event.actorId(),event.action(),event.previousStatus(),event.newStatus(),Timestamp.from(event.occurredAt()),new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("requestId",java.util.Objects.toString(org.slf4j.MDC.get("requestId"),""))));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { metrics.counter("tasks."+event.action().toLowerCase(java.util.Locale.ROOT)).increment(); }
        });
    }
}
