package com.parvez.task.query;

import java.time.Instant;
import java.util.UUID;
import com.parvez.task.domain.TaskPriority;
import com.parvez.task.domain.TaskStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;

public record TaskSearchRequest(
        @Min(0) Integer page,
        @Positive Integer size,
        TaskSortField sortBy,
        Sort.Direction direction,
        TaskStatus status,
        TaskPriority priority,
        UUID assignedTo,
        UUID teamId,
        UUID projectId,
        UUID createdBy,
        Instant createdFrom,
        Instant createdTo) {
}
