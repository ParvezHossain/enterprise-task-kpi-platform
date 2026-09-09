package com.parvez.auth.service;

/** Fixed messages only: never retain requests, rejected values or database causes. */
public final class RegistrationException extends RuntimeException {
    public enum Reason { INVALID_INPUT, EMAIL_UNAVAILABLE, ROLE_UNAVAILABLE, PERSISTENCE_FAILURE }

    private final Reason reason;

    public RegistrationException(Reason reason) {
        super("Registration failed: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
