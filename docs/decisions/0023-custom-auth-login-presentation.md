# ADR 0023: Custom Auth sign-in presentation

## Context and options

The generated form does not match the personal portal. Consider a custom
server-rendered form, a JavaScript authentication flow, or retaining the generated
page. Native navigation must preserve saved OAuth requests and BFF callbacks.

## Decision

Use a custom GET /login form and matching GET /logout confirmation with existing
Spring Security POST processing. Fixed classpath HTML templates replace only
HTML-escaped CSRF parameter/token and fixed visibility flags. Never render input
credentials or query values. No template engine or dependency is introduced.

Local CSS provides desktop/mobile presentation. Optional JavaScript adds password
visibility, Caps Lock and submission progress; forms work without JavaScript.
Submit natively so OAuth redirects navigate the browser instead of following them
through a cross-origin fetch. No credentials are stored or logged by the script.

HTML credential failures redirect to the fixed /login?error destination. API
callers without text/html keep the existing 401 Problem Details. Unknown/disabled
accounts and wrong passwords use identical public responses within each mode.
CSRF/rate-limit failures retain 403/429 Problem Details, including Retry-After.
No remember-me, recovery, account creation or provider buttons are invented.

## Consequences and compatibility

- Spring Security 7.1.1 loginPage/loginProcessingUrl configure the custom page;
  authentication, saved requests, CSRF, rotation and logout handlers remain active.
- Setting loginPage disables both generated login and logout pages in this version
  (verified in DefaultLoginPageConfigurer source). Explicit GET /logout rendering
  preserves confirmation and existing HTTP walkthroughs without ending sessions.
- Existing Auth CSP/no-store headers remain in force. Styles/scripts are local.
- Browser credential-error status changes from 401 to 302; API errors stay 401.
  GET /login?logout uses a fixed notice explaining independent app sessions.
- No schema, framework, dependency or setup configuration changes.

## Verification and references

AccountPortalIT covers server CSRF/query isolation/browser error modes; existing
OIDC integration tests exercise callbacks, API failure objects, logout and replay.
login-page.cjs covers production assets with browser fixture responses and native
submission with and without JavaScript.

[Spring Security 7.1 form login](https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/passwords/form.html)
describes custom GET rendering and the standard username/password/CSRF POST form.
