package com.parvez.task.web;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/metrics")
public class TaskMetricsController {
    private final JdbcTemplate jdbc;
    public TaskMetricsController(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private void machine(Jwt jwt) {
        Object claim=jwt.getClaims().get("scope");
        boolean allowed=claim instanceof Collection<?> scopes ? scopes.contains("task.metrics.read") :
                claim instanceof String text && Arrays.asList(text.split(" ")).contains("task.metrics.read");
        if(!allowed||!"kpi-sync".equals(jwt.getSubject())) throw new AccessDeniedException("Machine permission required");
    }
    public record MetricTask(UUID id,long version,UUID employeeId,UUID teamId,UUID projectId,String status,String priority,LocalDate dueDate,Instant createdAt,Instant startedAt,Instant completedAt,Instant closedAt,String creationRequestId) {}
    public record MetricPage(List<MetricTask> content,UUID upperId,UUID nextCursor) {}
    @GetMapping("/tasks")
    public MetricPage feed(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID cursor,@RequestParam(required=false) UUID upperId,@RequestParam(defaultValue="500") int size) {
        machine(jwt);if(size<1) throw new com.parvez.task.service.InvalidTaskQueryException("Size must be positive");size=Math.min(size,500);
        UUID upper=upperId;
        if(upper==null) {var ids=jdbc.queryForList("SELECT id FROM tasks ORDER BY id DESC LIMIT 1",UUID.class);if(ids.isEmpty()) return new MetricPage(List.of(),null,null);upper=ids.getFirst();}
        var values=new ArrayList<Object>();values.add(upper);
        String sql="SELECT tasks.*, (SELECT metadata::jsonb->>'requestId' FROM task_audit_log WHERE task_id=tasks.id AND action='CREATED' ORDER BY occurred_at LIMIT 1) AS creation_request_id FROM tasks WHERE id<=?";
        if(cursor!=null){sql+=" AND id>?";values.add(cursor);}sql+=" ORDER BY id LIMIT ?";values.add(size+1);
        List<MetricTask> rows=jdbc.query(sql,(r,n)-> new MetricTask(r.getObject("id",UUID.class),r.getLong("version"),r.getObject("assigned_to",UUID.class),r.getObject("team_id",UUID.class),r.getObject("project_id",UUID.class),r.getString("status"),r.getString("priority"),r.getObject("due_date",LocalDate.class),instant(r,"created_at"),instant(r,"started_at"),instant(r,"completed_at"),instant(r,"closed_at"),r.getString("creation_request_id")),values.toArray());
        boolean more=rows.size()>size;List<MetricTask> page=more?List.copyOf(rows.subList(0,size)):rows;
        return new MetricPage(page,upper,more?page.getLast().id():null);
    }
    private static Instant instant(java.sql.ResultSet rs,String name) throws java.sql.SQLException {var value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
    @GetMapping("/team-access")
    public Map<String,Boolean> access(@AuthenticationPrincipal Jwt jwt,@RequestParam UUID userId,@RequestParam UUID teamId) {
        machine(jwt);Boolean allowed=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM team_leaderships WHERE user_id=? AND team_id=?)",Boolean.class,userId,teamId);return Map.of("allowed",allowed);
    }
}
