package com.parvez.task.security;

import java.util.Set;
import java.util.UUID;
import com.parvez.task.authorization.TaskActor;
import com.parvez.task.authorization.TaskRole;
import com.parvez.task.persistence.TeamLeadershipRepository;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.Jwt;

@Component
public class CurrentTaskActorProvider {
    private final TaskActorFactory actorFactory;
    private final TeamLeadershipRepository teamLeaderships;

    public CurrentTaskActorProvider(TaskActorFactory actorFactory, TeamLeadershipRepository teamLeaderships) {
        this.actorFactory = actorFactory;
        this.teamLeaderships = teamLeaderships;
    }

    public TaskActor fromJwt(Jwt jwt) {
        TaskActor actor = actorFactory.fromJwt(jwt, Set.of());
        if (!actor.roles().contains(TaskRole.TEAM_LEADER)) {
            return actor;
        }
        try {
            return actorFactory.fromJwt(jwt, teamLeaderships.findManagedTeamIds(UUID.fromString(actor.subject())));
        } catch (IllegalArgumentException exception) {
            return actor;
        }
    }
}