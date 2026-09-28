package com.parvez.task.domain;

public final class InvalidTaskTransitionException extends RuntimeException {
    public InvalidTaskTransitionException(TaskStatus current, TaskStatus requested) {
        super("Task cannot transition from " + current + " to " + requested);
    }
}