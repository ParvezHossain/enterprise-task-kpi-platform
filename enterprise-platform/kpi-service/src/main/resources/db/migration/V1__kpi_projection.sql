CREATE TABLE kpi_sync_state (
 id INTEGER PRIMARY KEY CHECK(id=1),active_generation UUID,data_as_of TIMESTAMPTZ,last_successful_sync_at TIMESTAMPTZ,
 lease_owner UUID,lease_until TIMESTAMPTZ
);
INSERT INTO kpi_sync_state(id) VALUES(1);
CREATE TABLE metric_tasks (
 generation UUID NOT NULL,task_id UUID NOT NULL,version BIGINT NOT NULL,employee_id UUID,team_id UUID NOT NULL,project_id UUID NOT NULL,
 status VARCHAR(32) NOT NULL,priority VARCHAR(16) NOT NULL,due_date DATE,created_at TIMESTAMPTZ NOT NULL,
 started_at TIMESTAMPTZ,completed_at TIMESTAMPTZ,closed_at TIMESTAMPTZ,
 PRIMARY KEY(generation,task_id)
);
CREATE INDEX idx_metric_employee ON metric_tasks(generation,employee_id,created_at);
CREATE INDEX idx_metric_team ON metric_tasks(generation,team_id,created_at);
CREATE INDEX idx_metric_created ON metric_tasks(generation,created_at);
GRANT SELECT,INSERT,UPDATE,DELETE ON metric_tasks,kpi_sync_state TO "${runtimeRole}";
