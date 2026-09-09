# ADR 0002: Spring baseline

Status: Accepted design for TICKET-0002.

## Context

The stack contract requires Java 25+, Boot 4+, and Framework 7+ while keeping independent POMs.

## Decision

Select Boot 4.1.1, Framework 7.0.9, Security and its Authorization Server module 7.1.1 using the Boot BOM. Use Boot-managed JUnit 6 (Jupiter/Platform 6.0.3), as approved by the user and reflected in AGENTS.md.

## Alternatives considered

Boot 3 violates the contract; separately chosen Spring versions lose BOM alignment; snapshots are unnecessary.

## Consequences

Bootstrap tickets must use current APIs, pin build plugins, and verify Spring context, Mockito, and Testcontainers tests with the aligned JUnit 6 dependencies. See the architecture document for official version sources and migration notes.

See [architecture](../architecture.md) for the governing details and evidence.
