-- DEVELOPMENT / TEST ONLY. Never include this location in production migrations.
-- Fixed UUIDs make seed references deterministic. No users or credentials are seeded.
INSERT INTO roles (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('00000000-0000-0000-0000-000000000002', 'PROJECT_MANAGER'),
    ('00000000-0000-0000-0000-000000000003', 'TEAM_LEADER'),
    ('00000000-0000-0000-0000-000000000004', 'EMPLOYEE');
