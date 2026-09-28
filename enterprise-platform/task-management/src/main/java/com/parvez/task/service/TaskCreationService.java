package com.parvez.task.service;

import com.parvez.task.authorization.TaskAction;
import com.parvez.task.authorization.TaskActor;
import com.parvez.task.authorization.TaskAuthorizationPolicy;
import com.parvez.task.domain.TaskPriority;
import com.parvez.task.persistence.ProjectRepository;
import com.parvez.task.persistence.TaskEntity;
import com.parvez.task.persistence.TaskRepository;
import com.parvez.task.web.CreateTaskRequest;
import com.parvez.task.web.TaskResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCreationService {
    private final TaskAuthorizationPolicy authorizationPolicy;
    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public TaskCreationService(TaskAuthorizationPolicy authorizationPolicy,
            ProjectRepository projects, TaskRepository tasks) {
        this.authorizationPolicy = authorizationPolicy;
        this.projects = projects;
        this.tasks = tasks;
    }

    @Transactional
    public TaskResponse create(TaskActor actor, CreateTaskRequest request) {
        if (!authorizationPolicy.allows(actor, TaskAction.CREATE_TASK, null)) {
            throw new AccessDeniedException("Not permitted to create tasks");
        }
        if (!projects.existsByIdAndTeamId(request.projectId(), request.teamId())) {
            throw new InvalidTaskReferenceException();
        }

        TaskEntity saved = tasks.save(TaskEntity.draft(request.title(), request.description(),
                request.priority(), request.teamId(), request.projectId(), request.dueDate()));
        return new TaskResponse(saved.getId(), saved.getTitle(), saved.getDescription(), saved.getStatus(),
                TaskPriority.valueOf(saved.getPriority()), saved.getAssignedTo(), saved.getTeamId(),
                saved.getProjectId(), saved.getDueDate(), saved.getCreatedAt(), saved.getUpdatedAt(),
                saved.getVersion());
    }
}