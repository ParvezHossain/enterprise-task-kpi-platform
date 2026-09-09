# Security

## Initial auth-server bootstrap (superseded for protocol routes by TICKET-0105)

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

## Password hashing and registration

TICKET-0104 adds an internal registration service with Argon2id, fresh random salts,
and Bouncy Castle 1.85.2. The Spring encoder's `matches` operation verifies passwords;
raw credentials are never persisted. Passwords are preserved exactly and validated
before hashing (nonblank, 12–128 characters). See [ADR 0009](decisions/0009-registration-and-password-storage.md).

Registration always assigns EMPLOYEE and cannot accept privileged roles. Missing
EMPLOYEE reference data fails closed; normal local/docker and production startup
still do not provision roles or users automatically. Registration exposes no HTTP
route, login capability, or token issuance in this ticket.

Requests redact their string representation; result DTOs contain no credentials.
Service validation errors never retain constraint violations/rejected values, and
persistence errors discard database causes. Hibernate bind, extract, JDBC error,
and JDBC warning loggers are OFF to prevent hash/row disclosure. Preserve these
settings; do not enable SQL parameter, request body, or security-context logging.
General application TRACE is covered by the registration regression test.

The Testcontainers registration test scans captured stdout/stderr application logs
for the supplied password, persisted hash, any Argon2id hash prefix, and access/
refresh token canaries attached to the caller's security context. It exercises
success, invalid input, duplicate input, and an actual database failure, including
logging the safe request representation and exception stack traces. Tokens are
context canaries; this service neither consumes nor issues OAuth tokens. Coverage
must be extended to HTTP/login/OAuth flows when those tickets are implemented.


## OIDC and configured signing keys (TICKET-0105)

[ADR 0010](decisions/0010-oidc-confidential-pkce-clients.md) defines the current
protocol access policy. Discovery/JWKS and generated login are available; token
endpoints authenticate clients, authorize requires a logged-in user, and UserInfo
requires a bearer JWT. The application chain still denies other application/API
routes, keeping health public and Actuator internals private. Login retains CSRF,
HttpOnly/SameSite=Lax session cookies, and Spring's session fixation protection.
Deploy behind HTTPS and configure secure cookies/TLS termination appropriately.

RSA PKCS8 private and X509 public PEM resources plus issuer/key ID are mandatory;
startup fails closed for invalid keys. No application key generation or committed
private keys exist. Store production keys in mounted secret files with restricted
permissions. Preserve keys/issuer across restarts; overlapping key rotation is a
future extension, not implemented by replacing the current key file.

The two seeded clients are confidential first-party BFF registrations, with exact
redirects, required S256 PKCE and rotating refresh tokens. Their secrets are never
browser JS inputs. Consent is preapproved only for these operator-controlled
clients with restricted scopes; arbitrary clients require explicit consent policy.
Production excludes all seeds even when combined with dev/test profiles. The
manual provisioning utility writes generated credentials into ignored files and
is never called on application startup.

JWT role/permission claims come from the database, not request parameters. Every
token issuance reloads enabled status and authorities with a 256-authority cap.
Consumers must validate signature, issuer, audience, expiry, scopes and role/
ownership/tenant policies. A signed role claim alone is not resource authorization.
JWTs already issued to a subsequently disabled user last at most five minutes.

Authorization grants and opaque tokens are persisted by Spring's JDBC service;
treat that database and its backups as secret stores. Spring Security and JDBC
core logging remain INFO to avoid protocol/token/parameter TRACE output. Existing
Hibernate credential-sensitive loggers remain OFF. OAuth HTTP integration tests
scan application logs for passwords, both client secrets, codes, PKCE verifiers,
access/ID/refresh tokens, alongside TICKET-0104 registration coverage. Never enable
request-body or authorization-header logging at an ingress proxy.
