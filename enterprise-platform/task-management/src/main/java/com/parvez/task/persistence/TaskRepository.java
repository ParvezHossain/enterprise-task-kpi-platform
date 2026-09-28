package com.parvez.task.persistence;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface TaskRepository extends Repository<TaskEntity, UUID>, TaskSearchRepository {
    Optional<TaskEntity> findById(UUID id);

    TaskEntity save(TaskEntity task);
}