package com.parvez.task.service;

public final class InvalidTaskReferenceException extends RuntimeException {
    public InvalidTaskReferenceException() {
        this("The selected project does not belong to the selected team");
    }

    public InvalidTaskReferenceException(String message) {
        super(message);
    }
}