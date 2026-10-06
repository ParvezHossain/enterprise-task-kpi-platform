CREATE TABLE authentication_activity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event VARCHAR(32) NOT NULL CHECK (event IN ('LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'AUTH_LOGOUT')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_authentication_activity_user_time
    ON authentication_activity(user_id, occurred_at DESC, id DESC);
-- Runtime can append/read history, but cannot alter or erase it.
GRANT SELECT, INSERT ON authentication_activity TO "${runtimeRole}";
