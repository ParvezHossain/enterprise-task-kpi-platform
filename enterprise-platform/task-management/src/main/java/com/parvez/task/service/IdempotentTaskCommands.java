package com.parvez.task.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import com.parvez.task.authorization.*;
import com.parvez.task.persistence.TaskRepository;
import com.parvez.task.security.CurrentTaskActorProvider;
import com.parvez.task.web.TaskResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
@Service
public class IdempotentTaskCommands {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TaskCommandService commands;
    private final TaskRepository tasks;
    private final CurrentTaskActorProvider actors;
    private final TaskAuthorizationPolicy policy;
    public IdempotentTaskCommands(JdbcTemplate jdbc, ObjectMapper json, TaskCommandService commands,
            TaskRepository tasks, CurrentTaskActorProvider actors, TaskAuthorizationPolicy policy) {
        this.jdbc=jdbc; this.json=json; this.commands=commands; this.tasks=tasks; this.actors=actors; this.policy=policy;
    }
    @Transactional
    public TaskResponse execute(Jwt jwt, UUID id, String action, String key, long version, UUID employee) {
        if (key == null || !key.matches("[!-~]{1,128}")) throw new IdempotencyException(false);
        var actor=actors.fromJwt(jwt);
        var task=tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        var target=new TaskAuthorizationTarget(task.getTeamId(),task.getAssignedTo(),task.getStatus());
        TaskAction operation=switch(action) { case "approve" -> TaskAction.APPROVE_TASK; case "assign" -> TaskAction.ASSIGN_TASK; case "close" -> TaskAction.CLOSE_TASK; default -> throw new IllegalArgumentException("Unknown command"); };
        if (!policy.canAttempt(actor, operation) || (operation != TaskAction.CLOSE_TASK && !policy.allows(actor,operation,target))) throw new AccessDeniedException("Task command denied");
        String scope=hash(json.writeValueAsString(java.util.List.of(jwt.getIssuer().toString(),jwt.getSubject(),
                String.valueOf(jwt.getClaimAsString("azp")),String.valueOf(jwt.getClaimAsString("client_id")),"POST","/api/v1/tasks/"+id+"/"+action)));
        String payload=hash(action+":"+version+":"+employee);
        jdbc.execute("SET LOCAL lock_timeout = '2s'");
        jdbc.update("DELETE FROM task_command_keys WHERE scope_hash=? AND command_key=? AND expires_at<CURRENT_TIMESTAMP",scope,key);
        int claimed=jdbc.update("INSERT INTO task_command_keys(scope_hash,command_key,request_hash) VALUES(?,?,?) ON CONFLICT DO NOTHING",scope,key,payload);
        if (claimed==0) {
            return jdbc.queryForObject("SELECT request_hash,response_body FROM task_command_keys WHERE scope_hash=? AND command_key=?", (rs,n) -> {
                if (!payload.equals(rs.getString(1)) || rs.getString(2)==null) throw new IdempotencyException(true);
                return json.readValue(rs.getString(2),TaskResponse.class);
            },scope,key);
        }
        TaskResponse result=switch(action) {
            case "approve" -> commands.approve(actor,id,version);
            case "assign" -> commands.assign(actor,id,employee,version);
            case "close" -> commands.close(actor,id,version);
            default -> throw new IllegalArgumentException("Unknown command");
        };
        jdbc.update("UPDATE task_command_keys SET response_body=?,expires_at=CURRENT_TIMESTAMP+INTERVAL '24 hours' WHERE scope_hash=? AND command_key=?",json.writeValueAsString(result),scope,key);
        return result;
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
