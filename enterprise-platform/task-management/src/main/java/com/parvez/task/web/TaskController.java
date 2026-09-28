package com.parvez.task.web;

import java.net.URI;
import com.parvez.task.security.CurrentTaskActorProvider;
import com.parvez.task.service.TaskCreationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final CurrentTaskActorProvider actorProvider;
    private final TaskCreationService taskCreationService;

    public TaskController(CurrentTaskActorProvider actorProvider, TaskCreationService taskCreationService) {
        this.actorProvider = actorProvider;
        this.taskCreationService = taskCreationService;
    }

    @PostMapping
    ResponseEntity<TaskResponse> createTask(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskCreationService.create(actorProvider.fromJwt(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + response.id())).body(response);
    }
}