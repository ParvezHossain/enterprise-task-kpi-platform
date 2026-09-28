package com.parvez.task.persistence;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_memberships")
public class TeamMembershipEntity {
    @EmbeddedId
    private TeamMembershipId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeamMembershipEntity() {
    }
}