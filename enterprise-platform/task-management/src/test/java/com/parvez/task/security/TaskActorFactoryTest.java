package com.parvez.task.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.parvez.task.authorization.TaskRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TaskActorFactoryTest {
    private final TaskActorFactory factory = new TaskActorFactory();

    @Test
    void mapsAuthServerRoleClaimsAndIgnoresUnknownRoles() {
        UUID teamId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("123e4567-e89b-12d3-a456-426614174000")
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", List.of("TEAM_LEADER", "UNRECOGNIZED"))
                .build();

        var actor = factory.fromJwt(jwt, Set.of(teamId));

        assertThat(actor.subject()).isEqualTo(jwt.getSubject());
        assertThat(actor.roles()).containsExactly(TaskRole.TEAM_LEADER);
        assertThat(actor.managedTeamIds()).containsExactly(teamId);
    }
}