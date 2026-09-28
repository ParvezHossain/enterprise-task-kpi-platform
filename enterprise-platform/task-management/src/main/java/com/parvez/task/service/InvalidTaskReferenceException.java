package com.parvez.task.service;

public final class InvalidTaskReferenceException extends RuntimeException {
    public InvalidTaskReferenceException() {
        super("The selected project does not belong to the selected team");
    }
}