package com.parvez.task.authorization;

import java.util.Set;
import java.util.UUID;

public record TaskActor(String subject, Set<TaskRole> roles, Set<UUID> managedTeamIds) {
    public TaskActor {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        roles = Set.copyOf(roles);
        managedTeamIds = Set.copyOf(managedTeamIds);
    }
}