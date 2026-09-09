# ADR 0013: Auth observability

Status: Accepted — TICKET-0108

## Context and options

Operators need health probes, metrics, and correlated logs without exposing auth
data. Options were public metrics, existing session authentication, or a new
machine authentication mechanism. Logging could use Boot's JSON support or a
separate encoder; correlation could be request-local or use a distributed tracing
backend.

## Decision

Expose health, info, metrics and prometheus only. GET/HEAD health and info are
public; all other Actuator paths require authentication through the existing login
session. Anonymous sensitive requests receive 401 Problem Details even with HTML
Accept headers. CSRF remains enabled. No new credential store is added.

Use Boot's Logstash JSON format for console and configured file output.
An outer servlet filter supplies a fresh UUID requestId (also X-Request-ID in the
response) and a 32-character hexadecimal traceId. These are server-generated,
request-local correlation IDs, not distributed tracing spans. Ignore incoming
correlation headers. Restore the prior MDC in finally, including exceptional
exits. The current application has synchronous servlet endpoints; asynchronous
work and cross-service propagation would require explicit context propagation
and a tracing decision before introduction.

Log a completion event with status and durationMs after Problem Details processing.
Exclude raw URLs, query strings, bodies, headers, user identities and exception
messages. IDs are log fields only, never metric tags. Startup/background logs
without an HTTP request do not have request correlation fields.

## Consequences and compatibility

Session-authenticated clients can scrape Prometheus; an unattended scraper must
maintain a valid login session. Dedicated machine credentials are a future
security decision. Health continues to hide details/components and info is empty
unless safe contributors are configured. Other Actuator endpoints stay unexposed.

Boot 4.1.1 supplies the JSON encoder and manages Micrometer 1.17.1 and Prometheus
client 1.7.0. See architecture documentation for official compatibility sources.
No schema change or distributed collector is required.
