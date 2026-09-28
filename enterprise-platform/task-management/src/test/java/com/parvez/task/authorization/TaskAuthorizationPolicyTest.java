package com.parvez.task.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.parvez.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

class TaskAuthorizationPolicyTest {
    private static final UUID SUBJECT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID TEAM_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final Map<TaskRole, Set<TaskAction>> ROLE_ACTIONS = Map.of(
            TaskRole.ADMIN, Set.of(TaskAction.CREATE_TASK, TaskAction.READ_OWN_TASKS,
                    TaskAction.READ_TEAM_TASKS, TaskAction.READ_ALL_TASKS, TaskAction.APPROVE_TASK,
                    TaskAction.ASSIGN_TASK, TaskAction.CLOSE_TASK),
            TaskRole.PROJECT_MANAGER, Set.of(TaskAction.CREATE_TASK, TaskAction.READ_OWN_TASKS,
                    TaskAction.READ_TEAM_TASKS, TaskAction.READ_ALL_TASKS, TaskAction.CLOSE_TASK),
            TaskRole.TEAM_LEADER, Set.of(TaskAction.READ_OWN_TASKS, TaskAction.READ_TEAM_TASKS,
                    TaskAction.APPROVE_TASK, TaskAction.ASSIGN_TASK),
            TaskRole.EMPLOYEE, Set.of(TaskAction.READ_OWN_TASKS, TaskAction.START_TASK, TaskAction.COMPLETE_TASK));

    private final TaskAuthorizationPolicy policy = new TaskAuthorizationPolicy();

    @Test
    void evaluatesEveryRoleAndActionCell() {
        for (TaskRole role : TaskRole.values()) {
            TaskActor actor = actor(role, Set.of(TEAM_ID));
            for (TaskAction action : TaskAction.values()) {
                boolean expected = ROLE_ACTIONS.get(role).contains(action);
                assertThat(policy.allows(actor, action, targetFor(actor, action)))
                        .as("%s may %s", role, action)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void rejectsCompletionOfAnotherEmployeesTask() {
        TaskActor employee = actor(TaskRole.EMPLOYEE, Set.of());
        TaskAuthorizationTarget anotherEmployeesTask = new TaskAuthorizationTarget(
                TEAM_ID, UUID.randomUUID(), TaskStatus.IN_PROGRESS);

        assertThat(policy.allows(employee, TaskAction.COMPLETE_TASK, anotherEmployeesTask)).isFalse();
    }

    @Test
    void rejectsTeamLeaderActionsOutsideManagedTeams() {
        TaskActor leader = actor(TaskRole.TEAM_LEADER, Set.of());
        TaskAuthorizationTarget unownedTeamTask = new TaskAuthorizationTarget(
                TEAM_ID, UUID.randomUUID(), TaskStatus.DRAFT);

        assertThat(policy.allows(leader, TaskAction.READ_TEAM_TASKS, unownedTeamTask)).isFalse();
        assertThat(policy.allows(leader, TaskAction.APPROVE_TASK, unownedTeamTask)).isFalse();
        assertThat(policy.allows(leader, TaskAction.ASSIGN_TASK, unownedTeamTask)).isFalse();
    }

    @Test
    void rejectsStartAndCompletionWhenTaskIsInWrongState() {
        TaskActor employee = actor(TaskRole.EMPLOYEE, Set.of());

        assertThat(policy.allows(employee, TaskAction.START_TASK,
                new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.DRAFT))).isFalse();
        assertThat(policy.allows(employee, TaskAction.COMPLETE_TASK,
                new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.APPROVED))).isFalse();
    }

    @Test
    void onlyAllowsClosingCompletedTasks() {
        TaskActor manager = actor(TaskRole.PROJECT_MANAGER, Set.of());

        assertThat(policy.allows(manager, TaskAction.CLOSE_TASK,
                new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.IN_PROGRESS))).isFalse();
        assertThat(policy.allows(manager, TaskAction.CLOSE_TASK,
                new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.COMPLETED))).isTrue();
    }

    private static TaskActor actor(TaskRole role, Set<UUID> managedTeamIds) {
        return new TaskActor(SUBJECT_ID.toString(), Set.of(role), managedTeamIds);
    }

    private static TaskAuthorizationTarget targetFor(TaskActor actor, TaskAction action) {
        return switch (action) {
            case CREATE_TASK, READ_ALL_TASKS -> null;
            case READ_OWN_TASKS -> new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.DRAFT);
            case READ_TEAM_TASKS, APPROVE_TASK, ASSIGN_TASK ->
                    new TaskAuthorizationTarget(TEAM_ID, UUID.randomUUID(), TaskStatus.DRAFT);
            case START_TASK -> new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.APPROVED);
            case COMPLETE_TASK -> new TaskAuthorizationTarget(TEAM_ID, SUBJECT_ID, TaskStatus.IN_PROGRESS);
            case CLOSE_TASK -> new TaskAuthorizationTarget(TEAM_ID, UUID.randomUUID(), TaskStatus.COMPLETED);
        };
    }
}