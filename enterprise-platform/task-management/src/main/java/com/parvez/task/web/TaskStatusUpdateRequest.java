package com.parvez.task.web;

import com.parvez.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskStatusUpdateRequest(
        @NotNull TaskStatus status,
        @NotNull @PositiveOrZero Long version) {
}