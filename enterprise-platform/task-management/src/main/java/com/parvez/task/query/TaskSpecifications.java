package com.parvez.task.query;

import java.util.UUID;
import com.parvez.task.domain.TaskPriority;
import com.parvez.task.domain.TaskStatus;
import com.parvez.task.persistence.TaskEntity;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {
    private TaskSpecifications() {
    }

    public static Specification<TaskEntity> from(TaskSearchRequest request) {
        Specification<TaskEntity> specification = (root, query, builder) -> builder.conjunction();
        if (request.status() != null) {
            TaskStatus status = request.status();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (request.priority() != null) {
            TaskPriority priority = request.priority();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("priority"), priority.name()));
        }
        if (request.assignedTo() != null) {
            UUID assignedTo = request.assignedTo();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("assignedTo"), assignedTo));
        }
        if (request.teamId() != null) {
            UUID teamId = request.teamId();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("teamId"), teamId));
        }
        if (request.projectId() != null) {
            UUID projectId = request.projectId();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("projectId"), projectId));
        }
        if (request.createdBy() != null) {
            UUID createdBy = request.createdBy();
            specification = specification.and((root, query, builder) -> builder.equal(root.get("createdBy"), createdBy));
        }
        if (request.createdFrom() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
        }
        if (request.createdTo() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));
        }
        return specification;
    }

    public static Specification<TaskEntity> assignedTo(UUID subjectId) {
        return (root, query, builder) -> builder.equal(root.get("assignedTo"), subjectId);
    }

    public static Specification<TaskEntity> belongsToTeam(UUID teamId) {
        return (root, query, builder) -> builder.equal(root.get("teamId"), teamId);
    }
}
