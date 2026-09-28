package com.parvez.task.web;

import java.net.URI;
import java.util.Set;
import com.parvez.task.security.TaskActorFactory;
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
    private final TaskActorFactory actorFactory;
    private final TaskCreationService taskCreationService;

    public TaskController(TaskActorFactory actorFactory, TaskCreationService taskCreationService) {
        this.actorFactory = actorFactory;
        this.taskCreationService = taskCreationService;
    }

    @PostMapping
    ResponseEntity<TaskResponse> createTask(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskCreationService.create(actorFactory.fromJwt(jwt, Set.of()), request);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + response.id())).body(response);
    }
}