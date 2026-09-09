# Security

## Auth-server bootstrap

[ADR 0007](decisions/0007-auth-bootstrap-security.md) defines the initial policy:
only GET `/actuator/health` permits anonymous access. Other requests are denied;
anonymous GET requests receive 401. CSRF protection remains enabled, so unsafe
requests without a valid CSRF token may receive 403 before authorization.
Only health is exposed through Actuator, with no public component/details data.

No users, OAuth clients, or signing keys are provisioned. Default generated users
are disabled. Authorization Server and OpenAPI dependencies are installed for
later tickets, and their routes are not anonymously available. This bootstrap
does not implement login, token issuance, or business authorization yet.

Database credentials are supplied through environment variables. Runtime and
Flyway identities are separate; runtime access uses a non-superuser restricted to
`auth_db`. See the [service README](../enterprise-platform/auth-server/README.md)
for local provisioning. Hibernate validates schema, and Flyway owns schema changes.

## Identity reference data

The identity schema does not create users or grant authentication access. Four
reference roles are seeded only with explicit `dev`/`test` activation, never when
`prod` is active. Role-permission assignments are data for future backend policy
implementation; neither these entities nor their repositories are HTTP endpoints.
See [database](database.md) for environment isolation and migration ownership.
