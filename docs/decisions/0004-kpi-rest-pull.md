# ADR 0004: KPI REST pull

Status: Accepted design for TICKET-0002.

## Context

KPI needs SQL aggregates in its own database, and this learning project does not need Kafka or RabbitMQ.

## Decision

Poll a protected Task DTO feed every 60 seconds in bounded pages; stage and publish complete generations in KPI PostgreSQL.

## Alternatives considered

Broker events add operational complexity; direct Task database reads violate ownership; per-request full fetches couple dashboard availability to Task.

## Consequences

Results are eventually consistent. Preserve complete stale data on failure, expose age, and return 503 until initialized. Add a Task internal feed in TICKET-0302; follow architecture limits and authorization rules.

See [architecture](../architecture.md) for the governing details and evidence.
