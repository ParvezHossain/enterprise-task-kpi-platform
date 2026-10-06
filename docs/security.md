# Security

## Initial auth-server bootstrap (superseded by TICKET-0105 and TICKET-0108)

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

## Task Management resource server (TICKET-0201)

Task Management is a stateless bearer-token resource server. It retrieves signing
keys from the configurable Auth Server JWKS endpoint and validates the issuer,
signature, `exp`/`nbf`, and `task-management` audience before allowing a request to
reach a controller. The default issuer and JWKS URL target local Auth Server port
9000; deployments must configure `TASK_AUTH_ISSUER` and
`TASK_AUTH_JWK_SET_URI` together. The required audience defaults to
`task-management`. Only health and info actuator endpoints are public; all other
routes, including OpenAPI documentation, require authentication. JWT scopes and
application authorization policies will be enforced by the task APIs as they are
introduced; authentication alone does not authorize task ownership or tenant access.

## Task authorization policy (TICKET-0204)

`TaskAuthorizationPolicy` is the single role/action decision point. Its role matrix
is documented in [README section 20](../README.md#20-task-authorization-matrix).
The JWT `roles` claim is mapped to known Task roles; unknown roles fail closed.
The application supplies managed-team IDs from Task-owned membership data when
building the actor context. Team Leader actions are restricted to those teams,
employee start/completion requires the task assignee to match the JWT subject,
and transitions require `APPROVED` to start, `IN_PROGRESS` to complete, and
`COMPLETED` to close. Read-own and read-team list operations must apply their
subject/team predicates to the database query as well as pass policy checks.
Enable method security and have endpoint/service annotations delegate to this
policy rather than embedding role expressions in controllers.

For Task IDOR protection (TICKET-0207), an Employee requesting another
Employee's task receives `404 Not Found`, not `403`. This prevents a guessed task
ID from confirming the resource's existence, while Admin/PM access denials remain
403. The decision applies to employee-specific task operations; it does not replace
team/role authorization for broader queries.

### Local parallel-development stub (TICKET-0201a)

The optional `local-stub` profile replaces only the JWT decoder with an isolated
decoder that accepts unsigned `alg: none` JWTs carrying issuer `local-stub`, a
non-empty subject, the Task audience, and valid timestamps. It exists only to let
parallel developers exercise authenticated controller flows without a running Auth
Server; it does not verify a cryptographic signature and is not authentication.
The profile is never active by default, and startup fails before application
initialization if it is combined with `docker`, `prod`, or `production`. All other
profiles retain the real JWKS-backed issuer/signature/expiry/audience validation.

Authorization grants and opaque tokens are persisted by Spring's JDBC service;
treat that database and its backups as secret stores. Spring Security and JDBC
core logging remain INFO to avoid protocol/token/parameter TRACE output. Existing
Hibernate credential-sensitive loggers remain OFF. OAuth HTTP integration tests
scan application logs for passwords, both client secrets, codes, PKCE verifiers,
access/ID/refresh tokens, alongside TICKET-0104 registration coverage. Never enable
request-body or authorization-header logging at an ingress proxy.

## Login, logout, and consent policy (TICKET-0106)

`GET /login` serves the custom same-origin form, and `POST /login` authenticates
email/password with the session-bound CSRF token. Login rotates the session ID and
uses a saved OAuth authorization request when present; otherwise it redirects to
`/account`. The authenticated portal reads the current user's identity and activity
through session-protected DTO endpoints; public static assets contain no identity data.
Unknown users, wrong passwords, and disabled users get the same generic
401 Problem Details response for API/non-HTML callers. Browser HTML credential
failures redirect to `/login?error` with the same fixed generic message for all
three cases. Submitted credentials and query values are never echoed. Account and
authentication responses retain no-store cache headers.

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
wrong passwords for non-HTML callers; browser HTML credential failures use the
generic `/login?error` redirect described above. CSRF and session protections are unchanged.
See [the exact error contract](api.md#error-responses-ticket-0107).

## Observability access and log policy (TICKET-0108)

The current exposure supersedes the bootstrap policy: GET/HEAD health and info
are public; all remaining Actuator paths require an authenticated login session.
Metrics access is allowed for any authenticated user. Anonymous access gets 401
even when HTML is requested. Other Actuator endpoints remain unexposed.
Existing form login, CSRF, and logout/session invalidation remain in force.

JSON request completion events contain generated requestId/traceId, status and
durationMs. Incoming IDs are ignored; URLs, query parameters, headers, bodies and
identities are excluded from completion logs. Existing sensitive framework logging
restrictions remain active. See [ADR 0013](decisions/0013-auth-observability.md).

## Token failure regression coverage (TICKET-0109)

The PostgreSQL/HTTP OIDC suite checks that wrong secrets and unknown clients
receive 401 invalid_client, while cross-client and expired/revoked refresh grants
receive 400 invalid_grant. Revocation by a different authenticated client returns
400 invalid_client without invalidating the owner's grant; owner revocation persists in
PostgreSQL and remains successful when repeated. The revoked refresh token cannot
mint tokens. A correctly signed expired access token receives 401 invalid_token
at UserInfo with a sanitized Bearer challenge. These tests preserve the existing
RFC 9457 error response contract and check that token values do not enter logs.


Cross-client revocation follows the selected
[Spring Security 7.1.1 provider](https://github.com/spring-projects/spring-security/blob/7.1.1/oauth2/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2TokenRevocationAuthenticationProvider.java):
an existing grant owned by another client raises invalid_client; unknown token
revocation succeeds without a grant change.

## Auth container boundary (TICKET-0110)

The runtime uses UID/GID 10001 and a root-owned, read-only application JAR.
The documented run and smoke commands drop Linux capabilities, disallow new
privileges, and use a read-only root with a writable /tmp. Signing keys are mounted
read-only and must be readable by UID 10001; database credentials are injected
at runtime. Neither enters Docker build inputs or image layers. The image keeps
the existing authenticated metrics policy and public health/info policy.

## GitHub Actions delivery permissions

CI executes pull-request code with a read-only repository token via `pull_request`.
Checkout does not persist credentials. Only the default-branch push publishing job
receives `packages: write`; it loads the tested image from the same workflow run
and authenticates to GHCR using the short-lived `GITHUB_TOKEN`. Registry credentials
are cleared even after failure. No production signing keys, database credentials,
or deployment secrets are needed; tests generate disposable material. Action
revisions are pinned and checked weekly by Dependabot. Protect the default branch
with the verification/container checks described in [development](development.md#github-actions-ci-and-container-delivery).

## Task queries (TICKET-0208)

All-task queries require Admin or Project Manager. Team queries require an explicit
`teamId`; Team Leaders may select only a team from their server-loaded managed-team
set. Employees cannot use either broader route. My-task queries always AND the
authenticated subject with client filters in SQL, so an `assignedTo` parameter
cannot widen visibility. Detail and history reads use the same role/team/assignee
policy, including employee 404 concealment. New tasks derive `createdBy` from the
authenticated UUID subject; it is not accepted from the creation request.


## Remaining-feature security
Browser and bearer API chains have distinct purposes. Task/KPI resource APIs
accept Authorization bearer credentials and create no authentication sessions,
so CSRF is disabled there: browsers do not attach bearer credentials automatically.
The bff chain uses browser cookies and keeps CSRF enabled. Unsafe proxy requests
and local logout require the session CSRF token from /bff/session. JavaScript
never receives access/refresh tokens or client secrets.

TASK_SESSION and KPI_SESSION are HttpOnly/SameSite=Lax. Production must enable
Secure cookies and TLS. Sessions and authorized clients are local to each BFF
instance. Expired access tokens are refreshed server-side; refresh failure
requires login. BFF logout destroys its session but preserves independent Auth
session/grant state, consistent with the existing logout/revocation boundary.

Task CORS uses explicit configurable origins, intentional methods/headers and
credentials=false. The deployed browser uses same-origin BFF paths. Frontend
role gating is navigation only; Task and KPI services enforce roles/resources.

Task audit writes share the transition transaction; the runtime has no audit
UPDATE/DELETE permission or repository mutation API. Command replay rechecks
current roles/managed teams before returning stored data. Keys are scoped to
issuer/subject/client/path and expire after the documented 24-hour replay window.

KPI's confidential kpi-sync client has only task.metrics.read, a Task audience
and no human roles. Task's private feed checks both scope and machine subject.
This identity cannot use role-protected business commands. KPI never opens Task's
database. Current leadership checks are authenticated live Task calls and fail
closed during outages. Employees see only their own aggregates; Admin/PM can view
company metrics; Team Leaders need an authorized team for broader views.

Auth login limits are configurable, address-scoped and memory-bounded. They are
per-instance, reset on restart and do not trust caller forwarding headers.
Horizontal deployment should use an ingress/shared Redis limiter. CSP, nosniff
and Referrer-Policy are explicit; Spring HSTS remains conditional on HTTPS.
Auth custom login uses local scripts/styles; the existing Auth style CSP permits
inline styles for framework pages, while script policy remains same-origin.
Deployed app scripts/styles are local and require no unsafe-inline CSP allowance.

Incoming correlation IDs accept only 1–64 ASCII letters/digits/hyphens. Invalid
values are replaced with generated UUIDs; MDC is restored in finally blocks.
Audit and metric DTOs carry the originating creation ID, allowing async KPI logs
to link to the browser request. Never enable password/token/SQL bind logging.

Nginx access and request-error logs are disabled because upstream error messages
can include OAuth callback query strings. Backend structured completion logs and
container health checks provide request/status and availability diagnostics.
Do not enable raw proxy request logging on OAuth paths in production.

## Personal Auth portal and activity boundary

The authenticated `/account` page distinguishes identity-provider and application
sessions, offers trusted configured app links and a CSRF-protected Auth sign-out
form. GET `/` redirects there instead of denying the saved login destination.
Account/history DTOs derive ownership from the authenticated session UUID and
reload enabled identity state; Admin receives no cross-user history privilege.
Frontend identity/activity strings are rendered with textContent, and assets are
local under the existing CSP. No credential/token/session-id display is provided.

V4 stores only event UUID, existing user UUID, allowlisted kind and time. Record
interactive successful logins, failed credentials for known emails, form logout
and authenticated OIDC logout. Public failures stay generic in browser/API modes.
Unknown emails, CSRF rejection, rate limiting, token refresh, natural expiry and
BFF logout do not produce these per-user history rows. IP/user agent/session IDs
and credential material are excluded. Failed writes warn with fixed text and
increment auth.activity.write.failures, without blocking session invalidation.
Operators must monitor this counter and establish retention with bounded privileged
maintenance; this view is not guaranteed compliance/security audit coverage.

Auth logout ends its current browser session. Task/KPI BFF logout and refresh-token
revocation keep their independent existing boundaries; the portal neither displays
unobservable app session state nor offers an unsupported all-devices logout. See
[ADR 0022](decisions/0022-personal-auth-account-portal.md).
