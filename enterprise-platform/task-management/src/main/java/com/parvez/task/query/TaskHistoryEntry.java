package com.parvez.task.query;

import java.time.Instant;
import java.util.UUID;

public record TaskHistoryEntry(
        UUID id,
        UUID actorId,
        String action,
        String previousStatus,
        String newStatus,
        String metadata,
        Instant occurredAt) {
}
