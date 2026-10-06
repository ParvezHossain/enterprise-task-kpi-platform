package com.parvez.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Authentication metadata only: never credentials, tokens, addresses or session IDs. */
@Service
public class AuthActivityService {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    public AuthActivityService(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        metrics.counter("auth.activity.write.failures");
    }

    public record Entry(UUID id, String event, Instant occurredAt) { }
    public record Page(List<Entry> content, int page, int size, boolean hasNext) { }

    @EventListener
    public void signedIn(InteractiveAuthenticationSuccessEvent event) {
        record(event.getAuthentication(), "LOGIN_SUCCEEDED");
    }

    @EventListener
    public void signedOut(LogoutSuccessEvent event) {
        record(event.getAuthentication(), "AUTH_LOGOUT");
    }

    public void record(Authentication authentication, String event) {
        if (authentication == null || !authentication.isAuthenticated()) return;
        UUID user;
        try {
            user = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            return;
        }
        append(user, event);
    }

    public void failedLogin(String email) {
        if (email == null || email.length() > 254) return;
        try {
            var users = jdbc.queryForList("SELECT id FROM users WHERE email=? LIMIT 1", UUID.class,
                    email.strip().toLowerCase(Locale.ROOT));
            if (!users.isEmpty()) append(users.getFirst(), "LOGIN_FAILED");
        } catch (DataAccessException exception) {
            unavailable();
        }
    }

    private void append(UUID user, String event) {
        try {
            jdbc.update("INSERT INTO authentication_activity(id,user_id,event) VALUES(?,?,?)",
                    UUID.randomUUID(), user, event);
        } catch (DataAccessException exception) {
            // A missing audit write must not prevent session invalidation or leak database details.
            unavailable();
        }
    }

    private void unavailable() {
        metrics.counter("auth.activity.write.failures").increment();
        LoggerFactory.getLogger(AuthActivityService.class).warn("auth_activity_write_failed");
    }

    public Page history(UUID user, int page, int requestedSize) {
        if (page < 0 || page > 100000 || requestedSize < 1)
            throw new IllegalArgumentException("Invalid activity page");
        int size = Math.min(requestedSize, 50);
        var rows = jdbc.query("""
                SELECT id,event,occurred_at FROM authentication_activity
                WHERE user_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?
                """, (result, index) -> new Entry(result.getObject("id", UUID.class),
                result.getString("event"), result.getTimestamp("occurred_at").toInstant()),
                user, size + 1, (long) page * size);
        return new Page(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size,
                rows.size() > size);
    }
}
