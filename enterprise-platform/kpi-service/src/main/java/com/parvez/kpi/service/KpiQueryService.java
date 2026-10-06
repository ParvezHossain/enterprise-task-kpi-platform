package com.parvez.kpi.service;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

import com.parvez.kpi.integration.TaskClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class KpiQueryService {
    private final JdbcTemplate jdbc;
    private final TaskClient task;

    public KpiQueryService(JdbcTemplate jdbc, TaskClient task) {
        this.jdbc = jdbc;
        this.task = task;
    }

    public record Counts(long total, long completed, long onTime, long overdue, double meanCompletionHours,
                         double meanPriorityWeight) {
        public double completionPercentage() {
            return total == 0 ? 0 : 100.0 * completed / total;
        }

        public double onTimePercentage() {
            return completed == 0 ? 0 : 100.0 * onTime / completed;
        }
    }

    public record Summary(Counts counts, double completionPercentage, double onTimePercentage, BigDecimal score) {
    }

    public record Ranking(UUID employeeId, Counts counts, BigDecimal score) {
    }

    public record Page(List<Ranking> content, int page, int size, long totalElements, boolean hasNext) {
    }

    public record Trend(LocalDate month, long total, long completed, long overdue) {
    }

    public record Slice<T>(List<T> content, int page, int size, boolean hasNext) {
    }

    public record Envelope<T>(T data, Instant dataAsOf, Instant lastSuccessfulSyncAt, boolean stale) {
    }

    private record Scope(UUID generation, Instant dataAsOf, Instant successful, String where, List<Object> params) {
    }

    private boolean has(Jwt jwt, String role) {
        var roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private Scope scope(Jwt jwt, String view, UUID team, LocalDate from, LocalDate to) {
        boolean manager = has(jwt, "ADMIN") || has(jwt, "PROJECT_MANAGER");
        if (view.equals("company") && !manager) throw new AccessDeniedException("Company scope denied");
        if (!view.equals("me") && !view.equals("company")) {
            if (!manager) {
                if (!has(jwt, "TEAM_LEADER") || team == null || !task.manages(UUID.fromString(jwt.getSubject()), team))
                    throw new AccessDeniedException("Team scope denied");
            }
            if (view.equals("team") && team == null) throw new IllegalArgumentException("teamId required");
        }
        if (view.equals("me") && !(manager || has(jwt, "TEAM_LEADER") || has(jwt, "EMPLOYEE")))
            throw new AccessDeniedException("KPI role required");
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(365) : from;
        if (start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366)
            throw new IllegalArgumentException("Date range must be at most 366 days");
        Scope state = jdbc.queryForObject("SELECT active_generation,data_as_of,last_successful_sync_at FROM kpi_sync_state WHERE id=1", (r, n) -> {
            UUID generation = r.getObject(1, UUID.class);
            if (generation == null) throw new KpiUnavailableException();
            return new Scope(generation, r.getTimestamp(2).toInstant(), r.getTimestamp(3).toInstant(), "", List.of());
        });
        String where = "generation=? AND created_at>=? AND created_at<?";
        var params = new ArrayList<Object>();
        params.add(state.generation);
        params.add(Timestamp.from(start.atStartOfDay(ZoneOffset.UTC).toInstant()));
        params.add(Timestamp.from(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        if (view.equals("me")) {
            where += " AND employee_id=?";
            params.add(UUID.fromString(jwt.getSubject()));
        } else if (team != null) {
            where += " AND team_id=?";
            params.add(team);
        }
        return new Scope(state.generation, state.dataAsOf, state.successful, where, params);
    }

    private static final String AGG = "count(*) AS total, count(*) FILTER (WHERE status IN ('COMPLETED','CLOSED')) AS completed, count(*) FILTER (WHERE completed_at IS NOT NULL AND due_date IS NOT NULL AND (completed_at AT TIME ZONE 'UTC')::date<=due_date) AS on_time, count(*) FILTER (WHERE due_date<CURRENT_DATE AND status NOT IN ('COMPLETED','CLOSED')) AS overdue, COALESCE(avg(EXTRACT(EPOCH FROM(completed_at-started_at))/3600) FILTER (WHERE completed_at IS NOT NULL AND started_at IS NOT NULL),0) AS hours, COALESCE(avg(CASE priority WHEN 'URGENT' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END) FILTER (WHERE status IN ('COMPLETED','CLOSED')),0) AS weight";

    private Counts counts(java.sql.ResultSet r) throws java.sql.SQLException {
        return new Counts(r.getLong("total"), r.getLong("completed"), r.getLong("on_time"), r.getLong("overdue"), r.getDouble("hours"), r.getDouble("weight"));
    }

    private <T> Envelope<T> envelope(Scope scope, T data) {
        return new Envelope<>(data, scope.dataAsOf, scope.successful, Duration.between(scope.dataAsOf, Instant.now()).compareTo(Duration.ofMinutes(5)) > 0);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Envelope<Summary> summary(Jwt jwt, String view, UUID team, LocalDate from, LocalDate to) {
        Scope scope = scope(jwt, view, team, from, to);
        Counts counts = jdbc.queryForObject("SELECT " + AGG + " FROM metric_tasks WHERE " + scope.where, (r, n) -> counts(r), scope.params.toArray());
        return envelope(scope, new Summary(counts, counts.completionPercentage(), counts.onTimePercentage(), KpiScoring.score(counts.total, counts.completed, counts.onTime, counts.overdue, counts.meanPriorityWeight)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Envelope<Page> ranking(Jwt jwt, UUID team, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || page > 100000) throw new IllegalArgumentException("Invalid page");
        size = Math.min(size, 100);
        Scope scope = scope(jwt, "ranking", team, from, to);
        String where = scope.where + " AND employee_id IS NOT NULL";
        Long total = jdbc.queryForObject("SELECT count(DISTINCT employee_id) FROM metric_tasks WHERE " + where, Long.class, scope.params.toArray());
        String score = "ROUND(GREATEST(0, LEAST(100,100*(0.4*completed/NULLIF(total,0)+0.3*COALESCE(on_time::numeric/NULLIF(completed,0),0)+0.2*LEAST(completed/20.0,1)+0.1*weight/4-0.2*overdue/NULLIF(total,0)))),2)";
        var params = new ArrayList<>(scope.params);
        params.add(size);
        params.add((long) page * size);
        List<Ranking> rows = jdbc.query("WITH aggregates AS (SELECT employee_id," + AGG + " FROM metric_tasks WHERE " + where + " GROUP BY employee_id) SELECT *," + score + " AS score FROM aggregates ORDER BY score DESC,employee_id LIMIT ? OFFSET ?", (r, n) -> new Ranking(r.getObject("employee_id", UUID.class), counts(r), r.getBigDecimal("score")), params.toArray());
        return envelope(scope, new Page(rows, page, size, total, (long) (page + 1) * size < total));
    }

    private int sliceSize(int page, int size) {
        if (page < 0 || page > 100000 || size < 1) throw new IllegalArgumentException("Invalid page");
        return Math.min(size, 16);
    }

    private <T> Slice<T> slice(List<T> rows, int page, int size) {
        boolean next = rows.size() > size;
        return new Slice<>(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size, next);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Envelope<Slice<Trend>> trends(Jwt jwt, String view, UUID team, LocalDate from, LocalDate to, int page, int size) {
        size = sliceSize(page, size);
        Scope scope = scope(jwt, view, team, from, to);
        var params = new ArrayList<>(scope.params);
        params.add(size + 1);
        params.add((long) page * size);
        List<Trend> rows = jdbc.query("SELECT date_trunc('month',created_at AT TIME ZONE 'UTC')::date AS month," + AGG + " FROM metric_tasks WHERE " + scope.where + " GROUP BY month ORDER BY month LIMIT ? OFFSET ?", (r, n) -> new Trend(r.getObject("month", LocalDate.class), r.getLong("total"), r.getLong("completed"), r.getLong("overdue")), params.toArray());
        return envelope(scope, slice(rows, page, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Envelope<Slice<Map<String, Object>>> distribution(Jwt jwt, String view, UUID team, LocalDate from, LocalDate to, int page, int size) {
        size = sliceSize(page, size);
        Scope scope = scope(jwt, view, team, from, to);
        var params = new ArrayList<>(scope.params);
        params.add(size + 1);
        params.add((long) page * size);
        return envelope(scope, slice(jdbc.queryForList("SELECT status,count(*) AS count FROM metric_tasks WHERE " + scope.where + " GROUP BY status ORDER BY status LIMIT ? OFFSET ?", params.toArray()), page, size));
    }
}
