# Enterprise Task & KPI Platform

The application skeleton lives in [`enterprise-platform/`](enterprise-platform/README.md).
It contains three independent Maven backend projects and two static frontend
placeholders. The [auth server](enterprise-platform/auth-server/README.md) boots
against PostgreSQL and exposes a public health endpoint; Task and KPI remain
skeletons.

- [Directory structure and build commands](enterprise-platform/README.md)
- [Repository contract](AGENTS.md)
- [Development](docs/development.md)
- [Architecture](docs/architecture.md)
- [API, registration, and error contracts](docs/api.md)
- [Security](docs/security.md)
- [Login, OIDC and logout example requests](requests/auth.http)
- [Ticket backlog](docs/tickets/backlog.md)
