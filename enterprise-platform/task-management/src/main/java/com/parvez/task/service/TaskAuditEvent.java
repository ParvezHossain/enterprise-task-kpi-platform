package com.parvez.task.service;
import java.time.Instant;
import java.util.UUID;
public record TaskAuditEvent(UUID taskId, UUID actorId, String action, String previousStatus, String newStatus, Instant occurredAt) {}
