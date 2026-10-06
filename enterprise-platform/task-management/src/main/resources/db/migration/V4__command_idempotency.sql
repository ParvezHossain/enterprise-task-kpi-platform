CREATE TABLE task_command_keys (
 scope_hash VARCHAR(64) NOT NULL, command_key VARCHAR(128) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, response_body TEXT,
 expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '24 hours',
 PRIMARY KEY(scope_hash, command_key)
);
CREATE INDEX idx_task_command_expiry ON task_command_keys(expires_at);
GRANT SELECT, INSERT, UPDATE, DELETE ON task_command_keys TO "${runtimeRole}";
