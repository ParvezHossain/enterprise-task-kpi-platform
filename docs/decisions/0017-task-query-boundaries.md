# ADR 0017: Task query boundaries

Status: Accepted

## Context

TICKET-0208 requires filtered, role-scoped lists and bounded audit history.
Existing tasks lack creator identity.

## Options

Expose the full specification executor, filter loaded collections in Java, or use
a narrow paged repository fragment accepting Spring Data Specifications.

## Decision

Use the narrow fragment with database offset/limit and a matching count query.
Compose ownership/team predicates with filters before querying. Cap sizes at the
configured positive maximum, reject invalid offsets, and append UUID tie-breakers.
Authorize detail/history reads before returning DTOs. Add nullable creator UUID
through V3; record the authenticated creator for every new task.

## Consequences

The repository API does not expose unpaged searches. Counts add one query per
list; offset pagination can drift between requests under concurrent writes.
Legacy creator identity remains null rather than being guessed. Task responses
add `createdBy`; no dependency or framework API migration is required.
