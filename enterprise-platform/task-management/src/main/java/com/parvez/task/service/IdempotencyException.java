package com.parvez.task.service;
public class IdempotencyException extends RuntimeException {
    private final boolean conflict;
    public IdempotencyException(boolean conflict) { super(conflict ? "Idempotency key conflicts with another request." : "A printable Idempotency-Key of 1–128 characters is required."); this.conflict = conflict; }
    public boolean conflict() { return conflict; }
}
