package com.parvez.task.persistence;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface TeamLeadershipRepository extends Repository<TeamLeadershipEntity, TeamMembershipId> {
    @Query(value = "SELECT team_id FROM team_leaderships WHERE user_id = ?1 ORDER BY team_id LIMIT 1001", nativeQuery = true)
    Set<UUID> findManagedTeamIds(UUID userId);
}