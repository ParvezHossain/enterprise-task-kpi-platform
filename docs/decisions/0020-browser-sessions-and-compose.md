# ADR 0020: BFF sessions and local Compose
Status: Accepted.

## Context and options
Static apps need OAuth login without holding confidential secrets. Options were
public PKCE clients or confidential BFFs.

## Decision
An explicit bff profile uses Spring OAuth login, confidential clients, S256 PKCE
and server-side authorized clients. JavaScript receives identity, roles and CSRF
tokens, never OAuth tokens. Distinct HttpOnly/SameSite=Lax cookies identify each
app session. Browser CSRF is enabled; bearer requests remain stateless. The
authorized-client manager refreshes expired access tokens. Logout ends the BFF session.

Nginx serves shared assets on 8080/8081 and proxies BFF/OAuth callbacks.
The public issuer is http://127.0.0.1:9000, with internal token/JWKS URLs.
One local PostgreSQL container hosts three databases and separate runtime/migration
roles. Only browser/Auth ports bind on localhost. Compose is explicit development setup.

## Consequences and compatibility
Sessions are process-local; restarts require login. Horizontal scaling needs shared
sessions or routing affinity. BFF logout does not end the independent Auth session
or revoke grants. Production requires TLS, secure cookies and provisioned redirects.

Boot-managed OAuth client support remains on Boot 4.1.1. Tailwind 4 uses the separate
CLI and CSS import/source declarations. Plain JavaScript and Chart.js need no framework.
npm lockfiles and image digests make builds reproducible.
