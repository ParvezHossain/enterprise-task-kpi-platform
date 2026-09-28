package com.parvez.task.authorization;

import java.util.UUID;
import com.parvez.task.domain.TaskStatus;

public record TaskAuthorizationTarget(UUID teamId, UUID assignedTo, TaskStatus status) {
}