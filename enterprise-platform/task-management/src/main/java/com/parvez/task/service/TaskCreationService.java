package com.parvez.task.service;

import com.parvez.task.authorization.TaskAction;
import com.parvez.task.authorization.TaskActor;
import com.parvez.task.authorization.TaskAuthorizationPolicy;
import com.parvez.task.persistence.ProjectRepository;
import com.parvez.task.persistence.TaskEntity;
import com.parvez.task.persistence.TaskRepository;
import com.parvez.task.web.CreateTaskRequest;
import com.parvez.task.web.TaskResponse;
import com.parvez.task.web.TaskResponseMapper;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCreationService {
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final TaskAuthorizationPolicy authorizationPolicy;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskResponseMapper responseMapper;

    public TaskCreationService(jakarta.persistence.EntityManager entityManager, org.springframework.context.ApplicationEventPublisher events, TaskAuthorizationPolicy authorizationPolicy,
                               ProjectRepository projects, TaskRepository tasks, TaskResponseMapper responseMapper) {
        this.entityManager = entityManager;
        this.events = events;
        this.authorizationPolicy = authorizationPolicy;
        this.projects = projects;
        this.tasks = tasks;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public TaskResponse create(TaskActor actor, CreateTaskRequest request) {
        if (!authorizationPolicy.allows(actor, TaskAction.CREATE_TASK, null)) {
            throw new AccessDeniedException("Not permitted to create tasks");
        }
        if (!projects.existsByIdAndTeamId(request.projectId(), request.teamId())) {
            throw new InvalidTaskReferenceException();
        }

        UUID creatorId;
        try {
            creatorId = UUID.fromString(actor.subject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated subject is not a valid user identifier");
        }
        TaskEntity saved = tasks.save(TaskEntity.draft(request.title(), request.description(),
                request.priority(), request.teamId(), request.projectId(), creatorId,
                request.dueDate()));
        entityManager.flush();
        events.publishEvent(new TaskAuditEvent(saved.getId(), creatorId, "CREATED", null, "DRAFT", java.time.Instant.now()));
        return responseMapper.toResponse(saved);
    }
}
