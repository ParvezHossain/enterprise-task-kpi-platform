package com.parvez.task.persistence;

import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ProjectRepository extends Repository<ProjectEntity, UUID> {
    boolean existsByIdAndTeamId(UUID id, UUID teamId);
}