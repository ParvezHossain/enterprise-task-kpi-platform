# Enterprise Task & KPI Platform

The application lives in [`enterprise-platform/`](enterprise-platform/README.md).
It contains three independent Maven backend projects and two static frontend
placeholders. The [auth server](enterprise-platform/auth-server/README.md) boots
against PostgreSQL and exposes public health/info and authenticated metrics
endpoints. Task Management now has its resource-server bootstrap, initial schema,
domain lifecycle, and authorization policy; KPI remains a skeleton.

- [Directory structure and build commands](enterprise-platform/README.md)
- [Repository contract](AGENTS.md)
- [Development](docs/development.md)
- [Architecture](docs/architecture.md)
- [API, registration, and error contracts](docs/api.md)
- [Security](docs/security.md)
- [Login, OIDC and logout example requests](requests/auth.http)
- [Ticket backlog](docs/tickets/backlog.md)

The auth server also has a [standalone Docker image and smoke check](enterprise-platform/auth-server/README.md#build-and-run-the-container).

[GitHub Actions](.github/workflows/ci-cd.yml) verifies all three Maven projects and
smoke-tests the Auth container. Successful default-branch pushes publish the tested
image to GHCR. See [CI setup and delivery](docs/development.md#github-actions-ci-and-container-delivery)
for permissions, image naming, and required status checks.

## 20. Task Authorization Matrix

| Action | Admin | Project Manager | Team Leader | Employee |
| --- | --- | --- | --- | --- |
| Create task | Allow | Allow | Deny | Deny |
| Read own tasks | Allow | Allow | Allow | Allow |
| Read team tasks | Allow | Allow | Managed teams only | Deny |
| Read all tasks | Allow | Allow | Deny | Deny |
| Approve task | Allow | Deny | Managed teams only | Deny |
| Assign task | Allow | Deny | Managed teams only | Deny |
| Start assigned task | Deny | Deny | Deny | Own assigned task in `APPROVED` |
| Complete assigned task | Deny | Deny | Deny | Own assigned task in `IN_PROGRESS` |
| Close task | Completed tasks only | Completed tasks only | Deny | Deny |

The policy also checks resource context: Team Leaders may approve, assign, or read
only within teams they manage; employees may start/complete only tasks assigned to
their authenticated subject; and closing requires `COMPLETED`. List queries must
apply the authenticated subject/team scope in the database query. This matrix is
the source of truth for Task endpoint and method-security decisions.
