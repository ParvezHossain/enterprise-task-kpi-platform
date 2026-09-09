# ADR 0010: OIDC, persisted grants, and confidential PKCE clients

- Status: accepted
- Ticket: TICKET-0105
- Date: 2026-09-09

## Context and options

Both UI integrations need authorization code + PKCE, OIDC and refresh tokens.
Spring Authorization Server deliberately does not issue refresh tokens to public
clients. The options were public browser clients without refresh tokens, a custom
public-client refresh implementation, or confidential clients owned by future
backends for frontends (BFFs).

## Decision

Register `task-management-ui` and `kpi-ui` as confidential BFF clients using
`client_secret_basic`, required S256 PKCE, authorization-code and refresh-token
grants. This preserves Spring's protocol implementation and the requested refresh
flow. Browser JavaScript must never receive the client secrets or refresh tokens.
BFF implementations belong to the frontend integration ticket; the `.http` client
plays the confidential client during this ticket's verification. No BFF is claimed
to exist yet. No public-client refresh override is added.

Only the explicit dev/test Flyway location seeds these two first-party clients.
Each secret is supplied as an independently generated, prefixed Argon2id hash;
no plaintext default secret exists. Production clients require separately approved
provisioning. Redirects are exact loopback callback URIs on ports 8080/8081 for
development. Production callbacks must use HTTPS. Consent is preapproved only for
these operator-controlled first-party clients and their limited registered scopes;
new third-party clients must require consent. Dynamic client registration is off.

Require an explicit issuer and persistent RSA key pair from configured PEM resource
locations, plus a stable key ID. Never generate signing keys at application startup.
Reject missing, malformed, mismatched, or sub-2048-bit keys. Use RS256. Discovery
and JWKS are public; JWKS exposes public parameters only. HTTP issuers are allowed
only on loopback for development. Keep the same key material and issuer across
restarts/instances. Plan overlapping public keys before rotating a signing key;
this ticket loads one active pair, so replacing it immediately invalidates old JWTs.

Use JDBC registered clients, authorizations and consents with Flyway-managed tables.
Token lookups cap results at two and reject ambiguity; identity authorities cap at
256 and fail closed above that cap. The implementation retains the framework's
Jackson 3 mappers. SQL types follow its PostgreSQL schema guidance.

Use stable user UUIDs as `sub`. Access JWTs include current role names and
permission/role authorities, and resource audiences selected from authorized
scopes. Reload identity status and authorities during code exchange and refresh.
ID-token audience remains the client ID; `email` appears only for the email scope
and is not marked verified. Access lifetime is five minutes, code lifetime two
minutes, refresh lifetime eight hours with rotation. Disabled users cannot obtain
new tokens; already issued JWTs remain valid until expiry unless a resource server
adds online revocation checks.

## Consequences and migration/API notes

A generated Spring Security login form and database-backed `UserDetailsService`
are prerequisites pulled forward from TICKET-0106 to make the full flow runnable.
Login keeps CSRF and session fixation protection. Only protocol routes, login and
health become available; arbitrary application routes remain denied.

Spring Security 7.1 uses `HttpSecurity.oauth2AuthorizationServer` and configurer
classes under `org.springframework.security.config`. UserInfo requires bearer JWT
authentication on the protocol chain. A global authentication entry point disables
the generated login page, so the application chain uses a request-specific 401
entry point. JDBC authorization JSON uses allowlisted concrete `ArrayList` claim
values instead of Java immutable-list implementation types.

Database authorization rows contain bearer tokens and must be protected by DB
access controls, encrypted storage/backups, and limited retention. JDBC bind and
protocol TRACE logging remain disabled. An expiry cleanup job is not added here;
when implemented, it must delete in bounded batches. Framework JDBC persistence
is suitable for this baseline, not a claim of high-throughput or multi-node session
support. Sources and verification are in [architecture](../architecture.md) and
[development](../development.md).
