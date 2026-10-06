package com.parvez.task.query;

import java.util.List;
import java.util.UUID;

import com.parvez.task.authorization.TaskAction;
import com.parvez.task.authorization.TaskActor;
import com.parvez.task.authorization.TaskAuthorizationPolicy;
import com.parvez.task.authorization.TaskAuthorizationTarget;
import com.parvez.task.persistence.TaskAuditLogEntity;
import com.parvez.task.persistence.TaskAuditLogRepository;
import com.parvez.task.persistence.TaskEntity;
import com.parvez.task.persistence.TaskRepository;
import com.parvez.task.service.InvalidTaskQueryException;
import com.parvez.task.service.TaskNotFoundException;
import com.parvez.task.web.TaskResponse;
import com.parvez.task.web.TaskResponseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskQueryService {
    private final TaskAuthorizationPolicy authorizationPolicy;
    private final TaskRepository tasks;
    private final TaskAuditLogRepository auditLogs;
    private final TaskResponseMapper responseMapper;
    private final TaskQueryProperties properties;

    public TaskQueryService(TaskAuthorizationPolicy authorizationPolicy, TaskRepository tasks,
                            TaskAuditLogRepository auditLogs, TaskResponseMapper responseMapper,
                            TaskQueryProperties properties) {
        this.authorizationPolicy = authorizationPolicy;
        this.tasks = tasks;
        this.auditLogs = auditLogs;
        this.responseMapper = responseMapper;
        this.properties = properties;
    }

    public TaskPageResponse all(TaskActor actor, TaskSearchRequest request) {
        requireAllowed(actor, TaskAction.READ_ALL_TASKS, null);
        return search(TaskSpecifications.from(request), request);
    }

    public TaskPageResponse mine(TaskActor actor, TaskSearchRequest request) {
        requireAllowed(actor, TaskAction.READ_OWN_TASKS, null);
        UUID subjectId = subjectId(actor);
        Specification<TaskEntity> specification = TaskSpecifications.from(request)
                .and(TaskSpecifications.assignedTo(subjectId));
        return search(specification, request);
    }

    public TaskPageResponse team(TaskActor actor, TaskSearchRequest request) {
        UUID teamId = request.teamId();
        if (teamId == null) {
            throw new InvalidTaskQueryException("teamId is required for team task queries");
        }
        requireAllowed(actor, TaskAction.READ_TEAM_TASKS,
                new TaskAuthorizationTarget(teamId, null, null));
        Specification<TaskEntity> specification = TaskSpecifications.from(request)
                .and(TaskSpecifications.belongsToTeam(teamId));
        return search(specification, request);
    }

    public TaskResponse find(TaskActor actor, UUID taskId) {
        TaskEntity task = findTask(taskId);
        requireReadable(actor, task);
        return responseMapper.toResponse(task);
    }

    public TaskHistoryPageResponse history(TaskActor actor, UUID taskId, TaskSearchRequest request) {
        TaskEntity task = findTask(taskId);
        requireReadable(actor, task);
        Pageable pageable = pageRequest(request, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<TaskAuditLogEntity> history = auditLogs.findByTaskIdOrderByOccurredAtDesc(taskId, pageable);
        List<TaskHistoryEntry> content = history.getContent().stream()
                .map(event -> new TaskHistoryEntry(event.getId(), event.getActorId(), event.getAction(),
                        event.getPreviousStatus(), event.getNewStatus(), event.getMetadata(), event.getOccurredAt()))
                .toList();
        return new TaskHistoryPageResponse(content, history.getNumber(), history.getSize(),
                history.getTotalElements(), history.getTotalPages(), history.hasNext());
    }

    private TaskPageResponse search(Specification<TaskEntity> specification, TaskSearchRequest request) {
        Page<TaskEntity> page = tasks.search(specification, pageRequest(request, null));
        List<TaskResponse> content = page.getContent().stream().map(responseMapper::toResponse).toList();
        return new TaskPageResponse(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private Pageable pageRequest(TaskSearchRequest request, Sort forcedSort) {
        int page = request.page() == null ? 0 : request.page();
        int requestedSize = request.size() == null ? 20 : request.size();
        if (page < 0 || requestedSize < 1) {
            throw new InvalidTaskQueryException("page must be nonnegative and size must be positive");
        }
        if (request.createdFrom() != null && request.createdTo() != null
                && request.createdFrom().isAfter(request.createdTo())) {
            throw new InvalidTaskQueryException("createdFrom must not be after createdTo");
        }
        int size = Math.min(requestedSize, properties.maximumPageSize());
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidTaskQueryException("Requested page is too large");
        }

        Sort sort = forcedSort == null
                ? Sort.by(request.direction() == null ? Sort.Direction.DESC : request.direction(),
                (request.sortBy() == null ? TaskSortField.CREATED_AT : request.sortBy()).property())
                : forcedSort;
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(page, size, sort);
    }

    private void requireReadable(TaskActor actor, TaskEntity task) {
        TaskAuthorizationTarget target = target(task);
        if (authorizationPolicy.canReadTask(actor, target)) {
            return;
        }
        if (authorizationPolicy.shouldConcealEmployeeTask(actor, target)) {
            throw new TaskNotFoundException(task.getId());
        }
        throw new AccessDeniedException("Not permitted to read this task");
    }

    private void requireAllowed(TaskActor actor, TaskAction action, TaskAuthorizationTarget target) {
        if (!authorizationPolicy.allows(actor, action, target)) {
            throw new AccessDeniedException("Not permitted to query these tasks");
        }
    }

    private TaskEntity findTask(UUID taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private TaskAuthorizationTarget target(TaskEntity task) {
        return new TaskAuthorizationTarget(task.getTeamId(), task.getAssignedTo(), task.getStatus());
    }

    private UUID subjectId(TaskActor actor) {
        try {
            return UUID.fromString(actor.subject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated subject is not a valid user identifier");
        }
    }
}
