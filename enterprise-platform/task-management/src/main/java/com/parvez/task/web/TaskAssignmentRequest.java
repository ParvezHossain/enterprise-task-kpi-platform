package com.parvez.task.web;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskAssignmentRequest(
        @NotNull UUID employeeId,
        @NotNull @PositiveOrZero Long version) {
}