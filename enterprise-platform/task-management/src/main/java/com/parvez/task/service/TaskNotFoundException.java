package com.parvez.task.service;

import java.util.UUID;

public final class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID taskId) {
        super("Task not found: " + taskId);
    }
}