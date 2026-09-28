package com.parvez.task.service;

public final class TaskVersionConflictException extends RuntimeException {
    public TaskVersionConflictException() {
        super("The task was modified by another request");
    }
}