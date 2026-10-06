package com.parvez.task.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public class IdempotencyMaintenance {
    private final JdbcTemplate jdbc;

    public IdempotencyMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        jdbc.update("DELETE FROM task_command_keys WHERE (scope_hash,command_key) IN (SELECT scope_hash,command_key FROM task_command_keys WHERE expires_at<CURRENT_TIMESTAMP ORDER BY expires_at LIMIT 500)");
    }
}
