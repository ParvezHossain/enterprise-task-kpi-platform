package com.parvez.task.web;

import java.util.UUID;

import com.parvez.task.security.CurrentTaskActorProvider;
import com.parvez.task.service.TaskCommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}")
public class TaskCommandController {
    private final CurrentTaskActorProvider actorProvider;
    private final TaskCommandService commands;
    private final com.parvez.task.service.IdempotentTaskCommands idempotent;

    public TaskCommandController(CurrentTaskActorProvider actorProvider, TaskCommandService commands, com.parvez.task.service.IdempotentTaskCommands idempotent) {
        this.actorProvider = actorProvider;
        this.commands = commands;
        this.idempotent = idempotent;
    }

    @PostMapping("/approve")
    ResponseEntity<TaskResponse> approve(
            @PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TaskVersionRequest request
    ) {
        return ResponseEntity.ok(idempotent.execute(jwt, taskId, "approve", key, request.version(), null));
    }

    @PostMapping("/assign")
    ResponseEntity<TaskResponse> assign(
            @PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TaskAssignmentRequest request) {
        return ResponseEntity.ok(idempotent.execute(jwt, taskId, "assign", key, request.version(), request.employeeId()));
    }

    @PatchMapping("/status")
    ResponseEntity<TaskResponse> updateStatus(
            @PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(commands.updateStatus(actorProvider.fromJwt(jwt), taskId,
                request.status(), request.version()));
    }

    @PostMapping("/close")
    ResponseEntity<TaskResponse> close(
            @PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TaskVersionRequest request) {
        return ResponseEntity.ok(idempotent.execute(jwt, taskId, "close", key, request.version(), null));
    }
}