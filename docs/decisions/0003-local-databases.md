# ADR 0003: One local PostgreSQL instance

Status: Accepted design for TICKET-0002.

## Context

Three services need separate owned data stores without excessive local infrastructure.

## Decision

Use one container with auth_db, task_db, and kpi_db; isolate runtime and migration credentials and permissions.

## Alternatives considered

Three containers provide stronger process isolation at greater local cost; a shared database/schema weakens service boundaries.

## Consequences

One local failure affects every database. Provision databases separately from service-owned Flyway schema migrations; production topology can change independently.

See [architecture](../architecture.md) for the governing details and evidence.
