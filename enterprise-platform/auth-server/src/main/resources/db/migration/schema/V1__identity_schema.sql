-- Production schema only. No application users, roles, or permissions are seeded.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_canonical CHECK (email = lower(btrim(email)) AND email <> ''),
    CONSTRAINT ck_users_password_hash CHECK (btrim(password_hash) <> '')
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEADER', 'EMPLOYEE'))
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_permissions_name UNIQUE (name),
    CONSTRAINT ck_permissions_name CHECK (name = btrim(name) AND name <> '')
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Runtime access is limited to identity data; schema ownership stays with Flyway.
GRANT SELECT, INSERT, UPDATE, DELETE ON users, roles, permissions, user_roles, role_permissions
    TO "${runtimeRole}";
