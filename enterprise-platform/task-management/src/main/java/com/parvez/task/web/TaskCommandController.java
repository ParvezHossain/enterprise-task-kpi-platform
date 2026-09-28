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

    public TaskCommandController(CurrentTaskActorProvider actorProvider, TaskCommandService commands) {
        this.actorProvider = actorProvider;
        this.commands = commands;
    }

    @PostMapping("/approve")
    ResponseEntity<TaskResponse> approve(@PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskVersionRequest request) {
        return ResponseEntity.ok(commands.approve(actorProvider.fromJwt(jwt), taskId, request.version()));
    }

    @PostMapping("/assign")
    ResponseEntity<TaskResponse> assign(@PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskAssignmentRequest request) {
        return ResponseEntity.ok(commands.assign(actorProvider.fromJwt(jwt), taskId,
                request.employeeId(), request.version()));
    }

    @PatchMapping("/status")
    ResponseEntity<TaskResponse> updateStatus(@PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(commands.updateStatus(actorProvider.fromJwt(jwt), taskId,
                request.status(), request.version()));
    }

    @PostMapping("/close")
    ResponseEntity<TaskResponse> close(@PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskVersionRequest request) {
        return ResponseEntity.ok(commands.close(actorProvider.fromJwt(jwt), taskId, request.version()));
    }
}