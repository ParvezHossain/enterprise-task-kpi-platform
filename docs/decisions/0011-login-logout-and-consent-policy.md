# ADR 0011: Login, session logout, and first-party consent

- Status: accepted
- Ticket: TICKET-0106
- Date: 2026-09-09

## Context and options

TICKET-0105 introduced Spring's generated login form to exercise OAuth. Standalone
login still led to a denied root URL, and logout/consent behavior lacked full-flow
coverage. Options were custom credential/consent controllers or retaining Spring
Security 7.1.1's built-in forms and protocol handlers with explicit application
policy. We retain the framework handlers to preserve CSRF, session fixation,
security-context cleanup, saved-request handling and protocol state validation.

## Decision

Standalone login redirects to an authenticated `/account` page. Saved authorization
requests retain precedence. Invalid/unknown/disabled-user login uses the same generic
failure UI. GET `/logout` displays confirmation; POST requires the session's CSRF
token, invalidates the session, clears authentication and expires JSESSIONID before
redirecting to `/login?logout`. An old session cookie cannot restore authentication.

Logout ends this browser's authorization-server session, not all sessions or OAuth
grants for the identity. A BFF must revoke its refresh token through the authenticated
revocation endpoint and discard its local tokens/session when ending offline access.
Already issued JWTs may remain valid at offline resource servers until expiration.
We reject a global delete-all-grants logout handler because that would silently log
out other clients/devices and introduce cross-session revocation semantics.

Only reserved client IDs `task-management-ui` and `kpi-ui` may use a stored
`requireAuthorizationConsent=false` setting. They are operator-owned first-party
clients, with constrained scopes and exact redirects, within this platform's trust
boundary. Their dev/test registration already opts out of consent. A repository
policy forces consent for every other client, including incorrectly configured
third-party rows, while preserving PKCE and all other settings. An explicitly true
consent setting remains true even for first-party clients.

Spring renders and processes consent for additional clients. It may reuse consent
already granted for the requested scopes, and follows OIDC's openid-only exception;
requiring consent does not mean displaying a prompt on every request. Consent is
not authorization: user authentication, PKCE, scope/redirect validation and backend
role/ownership/tenant checks remain mandatory. Dynamic registration remains off.

## Consequences and compatibility

There are no new dependencies or migrations. User-facing forms use Spring's default
styling; `/account` is a minimal HTML landing page without identity/credential data.
The reserved first-party IDs must not be reassigned to third-party operators.
Production provisioning must preserve these trust assumptions. Tests exercise login,
code exchange, GET/POST logout, invalid CSRF, expired-session replay, token revocation,
and a third-party consent denial using actual HTTP and PostgreSQL.
