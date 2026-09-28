CREATE TABLE teams (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_teams_name CHECK (name = btrim(name) AND name <> '')
);

CREATE TABLE team_memberships (
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_team_memberships PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_memberships_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);
CREATE INDEX idx_team_memberships_user_id ON team_memberships(user_id);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_projects_team_id_id UNIQUE (team_id, id),
    CONSTRAINT ck_projects_name CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT fk_projects_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE RESTRICT
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    priority VARCHAR(16) NOT NULL,
    assigned_to UUID,
    team_id UUID NOT NULL,
    project_id UUID NOT NULL,
    due_date DATE,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tasks_title CHECK (title = btrim(title) AND title <> ''),
    CONSTRAINT ck_tasks_status CHECK (status = btrim(status) AND status <> ''),
    CONSTRAINT ck_tasks_priority CHECK (priority = btrim(priority) AND priority <> ''),
    CONSTRAINT ck_tasks_version CHECK (version >= 0),
    CONSTRAINT fk_tasks_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_project_team FOREIGN KEY (team_id, project_id)
        REFERENCES projects(team_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_assignee_team FOREIGN KEY (team_id, assigned_to)
        REFERENCES team_memberships(team_id, user_id) ON DELETE RESTRICT
);
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to);
CREATE INDEX idx_tasks_team_id ON tasks(team_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_created_at ON tasks(created_at);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_tasks_project_id ON tasks(project_id);

CREATE TABLE task_audit_log (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    metadata TEXT NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_audit_action CHECK (action = btrim(action) AND action <> ''),
    CONSTRAINT fk_task_audit_log_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE RESTRICT
);
CREATE INDEX idx_task_audit_log_task_occurred_at ON task_audit_log(task_id, occurred_at DESC);

-- User UUIDs are Auth Server subjects, not rows in task_db. Team memberships
-- provide the local assignment FK without crossing the service database boundary.
GRANT SELECT, INSERT, UPDATE, DELETE ON teams, team_memberships, projects, tasks
    TO "${runtimeRole}";
GRANT SELECT, INSERT ON task_audit_log TO "${runtimeRole}";