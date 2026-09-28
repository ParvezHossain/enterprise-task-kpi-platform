package com.parvez.task.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.parvez.task.domain.TaskPriority;
import com.parvez.task.domain.TaskStatus;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID assignedTo,
        UUID teamId,
        UUID projectId,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}