# ADR 0012: A single Problem Details boundary for authentication errors

Status: Accepted (TICKET-0107)

## Context and options

MVC controller advice cannot handle Spring Security filters or OAuth endpoint
writers. Advice alone leaves HTML, empty, and protocol JSON errors inconsistent.
Replacing every framework OAuth handler duplicates redirect and status semantics.

## Decision

Use `RestControllerAdvice` extending Framework 7 `ResponseEntityExceptionHandler`
for validation and exceptions, and a servlet response wrapper outside Security to
normalize all application 4xx/5xx bodies. Keep framework statuses and headers,
including authentication challenge codes and method headers. Strip Bearer
challenge descriptions/URIs because JWT decoders can include exception text. Remove error redirects'
Location headers only on 4xx/5xx; leave 302 login/OAuth redirects untouched.
The wrapper intercepts `sendError` to avoid container HTML error rendering.
Fixed messages and five RFC 9457 members provide a stable public contract. Only
allowlisted OAuth error codes are preserved as extensions. Login authentication
failure returns 401 instead of redirecting to a generated error page.

## Consequences and compatibility

Current auth responses are small, synchronous documents. The wrapper buffers each
response until the chain completes; streaming/large response endpoints would need
a different response strategy before introduction. No bodies are logged. HEAD
errors have no body, and connector-level rejection before servlet filters cannot
be normalized here. Callback redirects remain OAuth compliant; direct OAuth errors
use `application/problem+json` with the standard `error` field rather than
`application/json`, so consumers must support the Problem Details media type.

No dependencies, schema changes, or authentication policy changes are introduced.
Malformed-body and validation tests use an imported test-only controller/security
chain; production gains no registration HTTP endpoint. End-to-end protocol tests
verify that successful authorization, token rotation, consent and logout still work.
