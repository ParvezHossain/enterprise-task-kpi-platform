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

## Login, logout, and consent policy (TICKET-0106)

`GET /login` serves Spring Security's generated form, and `POST /login` authenticates
email/password with the session-bound CSRF token. Login rotates the session ID and
uses a saved OAuth authorization request when present; otherwise it redirects to
`/account`. That landing page requires authentication and exposes no identity data.
Unknown users, wrong passwords, and disabled users get the same generic
401 Problem Details response. Account and authentication responses retain no-store cache headers.

`GET /logout` only displays confirmation. `POST /logout` requires valid CSRF,
invalidates the HTTP session, clears its security context and CSRF state, explicitly
expires JSESSIONID, and redirects to `/login?logout`. Missing/foreign CSRF tokens
return 403 without logging out the user. Account access and OAuth authorization
require a new login afterward; replaying the previous session cookie cannot bypass
this. No arbitrary return-to URL is accepted by the local logout endpoint.

**Why first-party auto-approval is safe here:** `task-management-ui` and `kpi-ui`
are trusted, operator-owned applications within this platform's trust boundary,
not third-party clients receiving delegated access. Their scopes are explicitly
registered, callbacks are exact matches, and confidential-client authentication
plus S256 PKCE remain required. An extra consent click does not establish additional
trust for these controlled applications. Their existing dev/test registrations can
therefore auto-approve consent. Keep these reserved IDs under platform ownership;
never reuse them for an external application.

`ConsentEnforcingRegisteredClientRepository` permits consent opt-out only for those
two reserved IDs. Other clients are treated as consent-required even if their stored
flag says false. Explicit consent-required settings on first-party clients remain
respected. Third-party data scopes use Spring's generated consent page and validated
approval/denial flow. Previously granted consent may be reused; an openid-only
request follows Spring's OIDC exception. Auto-approval never bypasses authentication,
registered scopes/redirects, PKCE, or backend authorization checks. Dynamic client
registration stays disabled; production client provisioning is operator-controlled.

Local logout is deliberately **session-only**. Refresh tokens are separate OAuth
grants: clients that need to end offline access must call `POST /oauth2/revoke` with
their refresh token and client credentials and clear their own session/token store.
Offline JWT verification may still accept an issued access token until its five-minute
expiry. Logout does not silently remove another device's or client's grants. The
HTTP walkthrough demonstrates revocation plus logout, and tests separately confirm
these boundaries. See [ADR 0011](decisions/0011-login-logout-and-consent-policy.md).

## Safe error boundary (TICKET-0107)

MVC advice handles malformed input, Bean Validation, authentication/access denial,
registration failures, and unexpected exceptions. An outer servlet filter also
normalizes Security, CSRF, OAuth, firewall, and servlet error responses, since they
do not pass through controller advice. No exception messages, stack traces, request
bodies, or rejected values are logged by these handlers or returned to clients.
Only allowlisted OAuth error codes survive normalization; query strings are
excluded from Problem Details instances. Existing credential-log restrictions
remain in force. Failed login uses the same 401 body for unknown/disabled users and
wrong passwords, including HTML callers. CSRF and session protections are unchanged.
See [the exact error contract](api.md#error-responses-ticket-0107).
