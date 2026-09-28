package com.parvez.task.web;

import java.util.UUID;
import com.parvez.task.query.TaskHistoryPageResponse;
import com.parvez.task.query.TaskPageResponse;
import com.parvez.task.query.TaskQueryService;
import com.parvez.task.query.TaskSearchRequest;
import com.parvez.task.security.CurrentTaskActorProvider;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskQueryController {
    private final CurrentTaskActorProvider actorProvider;
    private final TaskQueryService queryService;

    public TaskQueryController(CurrentTaskActorProvider actorProvider, TaskQueryService queryService) {
        this.actorProvider = actorProvider;
        this.queryService = queryService;
    }

    @GetMapping
    TaskPageResponse all(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskSearchRequest request) {
        return queryService.all(actorProvider.fromJwt(jwt), request);
    }

    @GetMapping("/me")
    TaskPageResponse mine(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskSearchRequest request) {
        return queryService.mine(actorProvider.fromJwt(jwt), request);
    }

    @GetMapping("/team")
    TaskPageResponse team(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskSearchRequest request) {
        return queryService.team(actorProvider.fromJwt(jwt), request);
    }

    @GetMapping("/{taskId}/history")
    TaskHistoryPageResponse history(@PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskSearchRequest request) {
        return queryService.history(actorProvider.fromJwt(jwt), taskId, request);
    }

    @GetMapping("/{taskId}")
    TaskResponse find(@PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt) {
        return queryService.find(actorProvider.fromJwt(jwt), taskId);
    }
}