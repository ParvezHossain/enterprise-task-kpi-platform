# ADR 0022: Personal Auth account portal and activity

## Context

The generated login and minimal account landing page did not explain the independent
Auth, Task and KPI browser sessions. Opening Auth `/` could also save a forbidden
post-login destination. Users need a clear Auth sign-out action and actual activity
history rather than a simulated session-management dashboard.

## Options

1. Keep generated login and add a personal account portal with bounded own history.
2. Add a full identity administration console and cross-application/global logout.
3. Show static instructions only, without persisted activity.

## Decision

Choose option 1. GET `/` redirects to authenticated `/account`. Local HTML/CSS/JS
renders the current identity, configured application links, a session diagram,
explicit logout scope and paginated own history. Generated login/confirmation pages
and the existing CSRF-protected POST `/logout` remain supported.

Session-authenticated DTO endpoints expose `/api/v1/account` and
`/api/v1/account/activity`. Ownership comes solely from the authenticated UUID;
there is no user selector or admin-wide endpoint. Disabled users cannot read either
DTO. Page defaults to 0, size to 10; size caps at 50 and page at 100000.

Flyway V4 adds append/read-only `authentication_activity` rows: UUID, user UUID,
allowlisted event and timestamp. Record interactive successful logins, credential
failures for existing canonical emails, form logout and authenticated OIDC logout.
Never store passwords, hashes, tokens, raw email input, IPs, user agents or session IDs.
Unknown-email, rate-limited and CSRF-rejected attempts are not per-user history;
neither are token refresh, natural expiry or Task/KPI app logout.

An activity write failure emits only a fixed warning and
`auth.activity.write.failures`; it must not prevent logout or disclose DB details.
This personal history is not a guaranteed complete security/compliance audit.
History begins at deployment; existing events cannot be reconstructed. Runtime
has no UPDATE/DELETE privileges. Operators own retention/archive policy and must
use bounded batches through a privileged maintenance connection when removing old
records. No unbounded list/export or automated retention worker is introduced.

## Consequences and compatibility

- `/account` changes from a minimal page to a portal; `/` becomes a safe redirect.
- No framework/dependency versions change and no new template engine is required.
- Spring Security 7.1.1 interactive/login/logout event APIs are used. For OIDC,
  wrap the framework `OidcLogoutAuthenticationSuccessHandler` through the current
  `logoutResponseHandler` API, preserving validation and redirect handling.
- Portal logout ends the current Auth browser session only. Independent application
  sessions and refresh grants retain their documented boundaries. No button claims
  to end all sessions or revoke all tokens.
- Configured `AUTH_TASK_UI_URL`/`AUTH_KPI_UI_URL` use HTTPS except loopback development
  HTTP; credentials/query/fragment links are rejected. No arbitrary return URL is accepted.
- Static assets use same-origin CSS/JS, textContent for identity/history rendering,
  and the existing no-store/security headers. Account DTOs never serialize entities.

## Guidance

- [Spring Security logout and CSRF](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
- [OWASP logging guidance](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

Follow-up: [ADR 0023](0023-custom-auth-login-presentation.md) replaces the generated
login/confirmation presentation and defines the browser credential-error redirect;
the portal's history ownership and Auth/app logout boundaries remain in force.
