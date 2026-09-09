# ADR 0005: Durable command replay

Status: Accepted design for TICKET-0002.

## Context

A successful approve/assign/close response may be lost and retried across service instances.

## Decision

Commit a scoped unique idempotency key, request hash, response, task transition, and audit row in one database transaction; retain successful records for at least 24 hours.

## Alternatives considered

In-memory storage loses replay state; Redis adds infrastructure and atomicity issues; optimistic locking alone cannot replay responses.

## Consequences

Flyway adds durable storage. Require keys on these APIs and document 400/409/replay behavior. Reauthorize replays; bound concurrent waits and retention cleanup. Details and test cases are in architecture.md.

See [architecture](../architecture.md) for the governing details and evidence.
