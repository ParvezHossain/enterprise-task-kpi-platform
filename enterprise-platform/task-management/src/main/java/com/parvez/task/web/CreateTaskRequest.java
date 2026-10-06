package com.parvez.task.web;

import java.time.LocalDate;
import java.util.UUID;

import com.parvez.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @NotNull TaskPriority priority,
        @NotNull UUID projectId,
        @NotNull UUID teamId,
        LocalDate dueDate) {
    public CreateTaskRequest {
        if (title != null) {
            title = title.strip();
        }
    }
}