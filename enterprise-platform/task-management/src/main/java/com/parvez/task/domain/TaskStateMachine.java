package com.parvez.task.domain;

import java.util.Map;
import java.util.Set;

public final class TaskStateMachine {
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.DRAFT, Set.of(TaskStatus.APPROVED),
            TaskStatus.APPROVED, Set.of(TaskStatus.IN_PROGRESS),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.COMPLETED),
            TaskStatus.COMPLETED, Set.of(TaskStatus.CLOSED),
            TaskStatus.CLOSED, Set.of());

    public boolean canTransition(TaskStatus current, TaskStatus requested) {
        return current != null && requested != null
                && ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested);
    }

    public TaskStatus transition(TaskStatus current, TaskStatus requested) {
        if (!canTransition(current, requested)) {
            throw new InvalidTaskTransitionException(current, requested);
        }
        return requested;
    }
}