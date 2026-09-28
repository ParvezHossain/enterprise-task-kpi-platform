package com.parvez.task.authorization;

import java.util.Map;
import java.util.Set;
import com.parvez.task.domain.TaskStatus;
import org.springframework.stereotype.Component;

@Component("taskAuthorizationPolicy")
public final class TaskAuthorizationPolicy {
    private static final Map<TaskRole, Set<TaskAction>> ROLE_ACTIONS = Map.of(
            TaskRole.ADMIN, Set.of(
                    TaskAction.CREATE_TASK,
                    TaskAction.READ_OWN_TASKS,
                    TaskAction.READ_TEAM_TASKS,
                    TaskAction.READ_ALL_TASKS,
                    TaskAction.APPROVE_TASK,
                    TaskAction.ASSIGN_TASK,
                    TaskAction.CLOSE_TASK),
            TaskRole.PROJECT_MANAGER, Set.of(
                    TaskAction.CREATE_TASK,
                    TaskAction.READ_OWN_TASKS,
                    TaskAction.READ_TEAM_TASKS,
                    TaskAction.READ_ALL_TASKS,
                    TaskAction.CLOSE_TASK),
            TaskRole.TEAM_LEADER, Set.of(
                    TaskAction.READ_OWN_TASKS,
                    TaskAction.READ_TEAM_TASKS,
                    TaskAction.APPROVE_TASK,
                    TaskAction.ASSIGN_TASK),
            TaskRole.EMPLOYEE, Set.of(
                    TaskAction.READ_OWN_TASKS,
                    TaskAction.START_TASK,
                    TaskAction.COMPLETE_TASK));

    public boolean allows(TaskActor actor, TaskAction action, TaskAuthorizationTarget target) {
        if (actor == null || action == null
                || actor.roles().stream().noneMatch(role -> ROLE_ACTIONS.get(role).contains(action))) {
            return false;
        }

        return switch (action) {
            case CREATE_TASK, READ_ALL_TASKS -> true;
            case READ_OWN_TASKS -> target == null || isAssignedToActor(actor, target);
            case READ_TEAM_TASKS -> canReadTeam(actor, target);
            case APPROVE_TASK, ASSIGN_TASK -> canManageTaskTeam(actor, target);
            case START_TASK -> isAssignedToActor(actor, target)
                    && target.status() == TaskStatus.APPROVED;
            case COMPLETE_TASK -> isAssignedToActor(actor, target)
                    && target.status() == TaskStatus.IN_PROGRESS;
            case CLOSE_TASK -> target != null && target.status() == TaskStatus.COMPLETED;
        };
    }

    private boolean isAssignedToActor(TaskActor actor, TaskAuthorizationTarget target) {
        return target != null && target.assignedTo() != null
                && actor.subject().equals(target.assignedTo().toString());
    }

    private boolean canReadTeam(TaskActor actor, TaskAuthorizationTarget target) {
        if (target == null || target.teamId() == null) {
            return false;
        }
        return actor.roles().contains(TaskRole.ADMIN)
                || actor.roles().contains(TaskRole.PROJECT_MANAGER)
                || actor.managedTeamIds().contains(target.teamId());
    }

    private boolean canManageTaskTeam(TaskActor actor, TaskAuthorizationTarget target) {
        if (target == null || target.teamId() == null) {
            return false;
        }
        return actor.roles().contains(TaskRole.ADMIN)
                || (actor.roles().contains(TaskRole.TEAM_LEADER)
                && actor.managedTeamIds().contains(target.teamId()));
    }
}