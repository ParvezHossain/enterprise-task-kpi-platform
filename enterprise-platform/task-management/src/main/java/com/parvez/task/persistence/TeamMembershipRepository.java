package com.parvez.task.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface TeamMembershipRepository extends Repository<TeamMembershipEntity, TeamMembershipId> {
    @Query(value = "SELECT EXISTS (SELECT 1 FROM team_memberships WHERE team_id = ?1 AND user_id = ?2)",
            nativeQuery = true)
    boolean existsMembership(UUID teamId, UUID userId);
}