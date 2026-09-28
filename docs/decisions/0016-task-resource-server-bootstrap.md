# ADR 0016: Task Resource Server Bootstrap

## Status

Accepted

## Context

TICKET-0201 requires an independently buildable Task Management Spring Boot
service that accepts only access tokens issued for the Task API. Auth Server
access tokens are RS256-signed and include issuer, expiry, scopes, and a
`task-management` audience when a Task scope is authorized.

## Decision

Bootstrap Task with the repository's Java 25, Spring Boot 4.1.1, and Spring
Framework 7 baseline. Manage Spring dependencies through the Boot BOM and pin
springdoc 3.1.1, the Boot 4-compatible version already verified by Auth Server.
Use Spring Security OAuth2 Resource Server with the Auth Server JWKS endpoint for
signature verification and explicit issuer, timestamp, and audience validators.
Keep the service stateless; only health and info endpoints are public. Use its own
PostgreSQL database and Flyway credentials, with Hibernate set to `validate`.
For parallel local development only, a separate explicitly activated `local-stub`
profile may accept unsigned development JWTs. It cannot be combined with Docker or
production profiles and never replaces the default real-JWKS decoder.

## Options considered

- Trust Auth Server discovery at startup: rejected because the application should
  not need a live issuer metadata request during startup.
- Accept any correctly signed token and defer audience checks: rejected because
  OIDC-only, KPI, or otherwise misdirected tokens must not authenticate to Task.
- Reuse Auth Server's database or entities: rejected because services own separate
  schemas and only exchange API contracts.

## Consequences

Deployments configure issuer and JWKS URL together; the audience defaults to
`task-management`. The resource server verifies token identity but does not by
itself enforce task ownership, tenant boundaries, or fine-grained permissions.
TICKET-0202 adds the Task schema and migrations. Integration verification requires
Docker for PostgreSQL Testcontainers and uses a local JWKS fixture with real RSA
signatures. The local stub is intentionally non-cryptographic and must not be used
outside local parallel development.

## Compatibility evidence

Boot 4.1.1, Framework 7.0.9, and Security 7.1.1 are the repository's verified
baseline recorded in [architecture](../architecture.md). springdoc 3.1.1 is the
same Boot 4-compatible pinned release used by Auth Server.
