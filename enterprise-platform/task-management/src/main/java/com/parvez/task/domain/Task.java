package com.parvez.task.domain;

import java.util.Objects;
import java.util.UUID;

public record Task(UUID id, TaskStatus status) {
    public Task {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(status, "status is required");
    }

    public static Task draft(UUID id) {
        return new Task(id, TaskStatus.DRAFT);
    }

    public Task transitionTo(TaskStatus requested, TaskStateMachine stateMachine) {
        Objects.requireNonNull(stateMachine, "stateMachine is required");
        return new Task(id, stateMachine.transition(status, requested));
    }
}