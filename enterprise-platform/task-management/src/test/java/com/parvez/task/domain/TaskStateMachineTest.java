package com.parvez.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskStateMachineTest {
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TaskStatus.DRAFT, Set.of(TaskStatus.APPROVED),
            TaskStatus.APPROVED, Set.of(TaskStatus.IN_PROGRESS),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.COMPLETED),
            TaskStatus.COMPLETED, Set.of(TaskStatus.CLOSED),
            TaskStatus.CLOSED, Set.of());

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    @Test
    void allowsEveryDeclaredTransitionAndRejectsEveryOtherStatusPair() {
        for (TaskStatus current : TaskStatus.values()) {
            for (TaskStatus requested : TaskStatus.values()) {
                boolean expected = ALLOWED.get(current).contains(requested);
                assertThat(stateMachine.canTransition(current, requested))
                        .as("transition from %s to %s", current, requested)
                        .isEqualTo(expected);

                if (expected) {
                    assertThat(stateMachine.transition(current, requested)).isEqualTo(requested);
                } else {
                    assertThatThrownBy(() -> stateMachine.transition(current, requested))
                            .isInstanceOf(InvalidTaskTransitionException.class)
                            .hasMessageContaining("from " + current + " to " + requested);
                }
            }
        }
    }

    @Test
    void rejectsNullStatuses() {
        assertThat(stateMachine.canTransition(null, TaskStatus.DRAFT)).isFalse();
        assertThat(stateMachine.canTransition(TaskStatus.DRAFT, null)).isFalse();
        assertThatThrownBy(() -> stateMachine.transition(null, TaskStatus.DRAFT))
                .isInstanceOf(InvalidTaskTransitionException.class);
    }

    @Test
    void aggregateReturnsNewInstanceOnValidTransition() {
        Task task = Task.draft(UUID.randomUUID());

        Task approved = task.transitionTo(TaskStatus.APPROVED, stateMachine);

        assertThat(task.status()).isEqualTo(TaskStatus.DRAFT);
        assertThat(approved.status()).isEqualTo(TaskStatus.APPROVED);
        assertThat(approved.id()).isEqualTo(task.id());
    }

    @Test
    void aggregateDoesNotChangeWhenTransitionIsInvalid() {
        Task task = Task.draft(UUID.randomUUID());

        assertThatThrownBy(() -> task.transitionTo(TaskStatus.CLOSED, stateMachine))
                .isInstanceOf(InvalidTaskTransitionException.class);

        assertThat(task.status()).isEqualTo(TaskStatus.DRAFT);
    }
}