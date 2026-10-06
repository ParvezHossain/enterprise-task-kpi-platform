# ADR 0021: Login rate limits and headers
Status: Accepted.

## Context and options
Login abuse and browser content injection require safeguards. Options were a
distributed limiter, an in-process limiter, or no limit.

## Decision
Auth permits 20 POST login requests per remote address in 60 seconds by default.
Both settings are configurable. At most 10,000 live addresses are tracked; a full
map fails closed for new addresses. Expired windows are removed during checks.
Untrusted forwarding headers are ignored. Return 429 Problem Details and Retry-After.

Security chains include CSP, X-Content-Type-Options and Referrer-Policy. Spring's
default HSTS applies to secure requests. Nginx restricts scripts/styles/connections
to self. Auth's generated login page needs inline styles, permitted only there.

## Consequences
Limits are not shared between instances and restart resets them. Horizontal
deployment requires an ingress or shared Redis limiter with trusted address
handling. Password/token logging remains forbidden.
