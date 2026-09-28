package com.parvez.task.web;

import com.parvez.task.domain.TaskPriority;
import com.parvez.task.persistence.TaskEntity;
import org.springframework.stereotype.Component;

@Component
public class TaskResponseMapper {
    public TaskResponse toResponse(TaskEntity task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                TaskPriority.valueOf(task.getPriority()), task.getAssignedTo(), task.getTeamId(),
                task.getProjectId(), task.getCreatedBy(), task.getDueDate(), task.getCreatedAt(), task.getUpdatedAt(),
                task.getVersion());
    }
}