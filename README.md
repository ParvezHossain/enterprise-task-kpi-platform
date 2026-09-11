# Enterprise Task & KPI Platform

The application skeleton lives in [`enterprise-platform/`](enterprise-platform/README.md).
It contains three independent Maven backend projects and two static frontend
placeholders. The [auth server](enterprise-platform/auth-server/README.md) boots
against PostgreSQL and exposes public health/info and authenticated metrics endpoints; Task and KPI remain
skeletons.

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
