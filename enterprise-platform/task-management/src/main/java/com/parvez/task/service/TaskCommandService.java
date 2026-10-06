package com.parvez.task.service;

import java.time.Instant;
import java.util.UUID;
import com.parvez.task.authorization.TaskAction;
import com.parvez.task.authorization.TaskActor;
import com.parvez.task.authorization.TaskAuthorizationPolicy;
import com.parvez.task.authorization.TaskAuthorizationTarget;
import com.parvez.task.domain.TaskStateMachine;
import com.parvez.task.domain.TaskStatus;
import com.parvez.task.persistence.TaskEntity;
import com.parvez.task.persistence.TaskRepository;
import com.parvez.task.persistence.TeamMembershipRepository;
import com.parvez.task.web.TaskResponse;
import com.parvez.task.web.TaskResponseMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommandService {
    private final org.springframework.context.ApplicationEventPublisher events;
    private final TaskAuthorizationPolicy authorizationPolicy;
    private final TaskRepository tasks;
    private final TeamMembershipRepository memberships;
    private final TaskStateMachine stateMachine;
    private final TaskResponseMapper responseMapper;
    private final EntityManager entityManager;

    public TaskCommandService(org.springframework.context.ApplicationEventPublisher events, TaskAuthorizationPolicy authorizationPolicy, TaskRepository tasks,
            TeamMembershipRepository memberships, TaskStateMachine stateMachine,
            TaskResponseMapper responseMapper, EntityManager entityManager) {
        this.events=events;
        this.authorizationPolicy = authorizationPolicy;
        this.tasks = tasks;
        this.memberships = memberships;
        this.stateMachine = stateMachine;
        this.responseMapper = responseMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public TaskResponse approve(TaskActor actor, UUID taskId, long expectedVersion) {
        TaskEntity task = findTask(taskId);
        if (!authorizationPolicy.allows(actor, TaskAction.APPROVE_TASK, target(task))) {
            throw new AccessDeniedException("Not permitted to approve this task");
        }
        verifyVersion(task, expectedVersion);
        TaskStatus approved = stateMachine.transition(task.getStatus(), TaskStatus.APPROVED);
        TaskStatus previous=task.getStatus();
        task.updateStatus(approved, Instant.now());
        flush(task);
        events.publishEvent(new TaskAuditEvent(task.getId(),UUID.fromString(actor.subject()),"APPROVED",previous.name(),task.getStatus().name(),Instant.now()));
        return responseMapper.toResponse(task);
    }

    @Transactional
    public TaskResponse assign(TaskActor actor, UUID taskId, UUID employeeId, long expectedVersion) {
        TaskEntity task = findTask(taskId);
        if (!authorizationPolicy.allows(actor, TaskAction.ASSIGN_TASK, target(task))) {
            throw new AccessDeniedException("Not permitted to assign this task");
        }
        verifyVersion(task, expectedVersion);
        if (task.getStatus() != TaskStatus.APPROVED || task.getAssignedTo() != null) {
            throw new TaskVersionConflictException();
        }
        if (!memberships.existsMembership(task.getTeamId(), employeeId)) {
            throw new InvalidTaskReferenceException("Assignee must be a member of the task's team");
        }

        task.assignTo(employeeId, Instant.now());
        flush(task);
        events.publishEvent(new TaskAuditEvent(task.getId(),UUID.fromString(actor.subject()),"ASSIGNED",task.getStatus().name(),task.getStatus().name(),Instant.now()));
        return responseMapper.toResponse(task);
    }

    @Transactional
    public TaskResponse updateStatus(TaskActor actor, UUID taskId, TaskStatus requestedStatus, long expectedVersion) {
        TaskEntity task = findTask(taskId);
        TaskAuthorizationTarget target = target(task);
        if (authorizationPolicy.shouldConcealEmployeeTask(actor, target)) {
            throw new TaskNotFoundException(taskId);
        }

        TaskAction action = switch (requestedStatus) {
            case IN_PROGRESS -> TaskAction.START_TASK;
            case COMPLETED -> TaskAction.COMPLETE_TASK;
            default -> throw new com.parvez.task.domain.InvalidTaskTransitionException(
                    task.getStatus(), requestedStatus);
        };
        if (!authorizationPolicy.canAttempt(actor, action)) {
            throw new AccessDeniedException("Not permitted to update this task");
        }
        verifyVersion(task, expectedVersion);
        TaskStatus nextStatus = stateMachine.transition(task.getStatus(), requestedStatus);
        if (!authorizationPolicy.allows(actor, action, target)) {
            throw new AccessDeniedException("Not permitted to update this task");
        }

        TaskStatus previous=task.getStatus();
        task.updateStatus(nextStatus, Instant.now());
        flush(task);
        events.publishEvent(new TaskAuditEvent(task.getId(),UUID.fromString(actor.subject()),requestedStatus == TaskStatus.IN_PROGRESS ? "STARTED" : "COMPLETED",previous.name(),task.getStatus().name(),Instant.now()));
        return responseMapper.toResponse(task);
    }

    @Transactional
    public TaskResponse close(TaskActor actor, UUID taskId, long expectedVersion) {
        TaskEntity task = findTask(taskId);
        TaskAuthorizationTarget target = target(task);
        if (authorizationPolicy.shouldConcealEmployeeTask(actor, target)) {
            throw new TaskNotFoundException(taskId);
        }
        if (!authorizationPolicy.canAttempt(actor, TaskAction.CLOSE_TASK)) {
            throw new AccessDeniedException("Not permitted to close this task");
        }
        verifyVersion(task, expectedVersion);
        TaskStatus closed = stateMachine.transition(task.getStatus(), TaskStatus.CLOSED);
        if (!authorizationPolicy.allows(actor, TaskAction.CLOSE_TASK, target)) {
            throw new AccessDeniedException("Not permitted to close this task");
        }

        TaskStatus previous=task.getStatus();
        task.updateStatus(closed, Instant.now());
        flush(task);
        events.publishEvent(new TaskAuditEvent(task.getId(),UUID.fromString(actor.subject()),"CLOSED",previous.name(),task.getStatus().name(),Instant.now()));
        return responseMapper.toResponse(task);
    }

    private TaskEntity findTask(UUID taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private TaskAuthorizationTarget target(TaskEntity task) {
        return new TaskAuthorizationTarget(task.getTeamId(), task.getAssignedTo(), task.getStatus());
    }

    private void verifyVersion(TaskEntity task, long expectedVersion) {
        if (task.getVersion() != expectedVersion) {
            throw new TaskVersionConflictException();
        }
    }

    private void flush(TaskEntity task) {
        try {
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new TaskVersionConflictException();
        }
    }
}