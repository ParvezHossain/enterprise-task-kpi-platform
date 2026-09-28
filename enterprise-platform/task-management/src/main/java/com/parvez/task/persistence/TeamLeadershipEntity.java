package com.parvez.task.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_leaderships")
public class TeamLeadershipEntity {
    @EmbeddedId
    private TeamMembershipId id;

    protected TeamLeadershipEntity() {
    }
}