package com.parvez.task.service;

public final class InvalidTaskQueryException extends RuntimeException {
    public InvalidTaskQueryException(String message) {
        super(message);
    }
}