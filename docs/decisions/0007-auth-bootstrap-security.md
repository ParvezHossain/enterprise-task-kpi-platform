# ADR 0007: Auth bootstrap access policy

Status: Accepted for TICKET-0101.

## Context

The first auth-server ticket requires a bootable application and public health.
User storage, OAuth clients, signing keys, and login flows are later tickets.

## Decision

Permit only anonymous GET `/actuator/health`. Deny every other request with a
security filter chain and return 401 for unauthenticated access. Expose only the
Actuator health endpoint, without component details. Retain CSRF protection.
Disable generated default users with Boot 4's user-details auto-configuration
exclusion; do not provision temporary users, passwords, OAuth clients, or keys.

## Alternatives considered

A blanket permit-all chain would expose future endpoints accidentally. Generated
users and demo OAuth clients would create undocumented credentials and overlap
with the subsequent authentication tickets.

## Consequences

The Authorization Server and springdoc libraries are present, but OAuth flows and
Swagger access are not yet enabled. TICKET-0105 will add the authorization-server
chain and OIDC configuration; later access rules must preserve anonymous health
and explicitly secure every new endpoint. The integration test checks health and
rejected anonymous requests over real HTTP with PostgreSQL running.

See [security](../security.md) and [architecture](../architecture.md).
