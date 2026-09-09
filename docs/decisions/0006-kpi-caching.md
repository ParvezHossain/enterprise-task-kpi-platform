# ADR 0006: No initial aggregate cache

Status: Accepted design for TICKET-0002.

## Context

The local KPI projection already avoids synchronous Task calls for dashboard reads.

## Decision

Calculate aggregates with SQL and add no aggregate response cache initially.

## Alternatives considered

Immediate Caffeine adds staleness and invalidation work; Redis adds another service without measured need.

## Consequences

Measure first. If needed, introduce bounded Caffeine with a 30-second TTL and generation/authorization-aware keys as specified in architecture.md. No runtime API change in this ticket.

See [architecture](../architecture.md) for the governing details and evidence.
