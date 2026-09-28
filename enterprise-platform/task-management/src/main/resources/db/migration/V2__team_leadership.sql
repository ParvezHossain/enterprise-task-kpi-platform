CREATE TABLE team_leaderships (
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT pk_team_leaderships PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_leaderships_membership FOREIGN KEY (team_id, user_id)
        REFERENCES team_memberships(team_id, user_id) ON DELETE CASCADE
);
CREATE INDEX idx_team_leaderships_user_id ON team_leaderships(user_id);

-- Leadership is provisioned by the migration/operator role, not the app runtime.
GRANT SELECT ON team_leaderships TO "${runtimeRole}";