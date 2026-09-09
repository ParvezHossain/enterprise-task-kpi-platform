# ADR 0008: Identity persistence and seed isolation

Status: Identity/schema design accepted; exact permission catalog awaiting the
master-spec list from the user.

## Context

TICKET-0103 needs User, Role, Permission, and queryable development reference data.
TICKET-0102 has not yet supplied the required tables in this checkout.

## Decision

Add only the five prerequisite identity tables in a production-safe V1 schema
migration. Map UUID entities in `com.parvez.auth.domain` with lazy associations,
unique names/email, and no cascading removal of shared roles or permissions.
Expose selective repositories without unbounded collection queries.

Place development/test seed SQL in a separate Flyway location. Require explicit
`dev` or `test` activation and exclude that location whenever `prod` is active.
Local/docker connection profiles alone do not opt into seeds. Do not seed users or
assign privileges without the specified permission catalog and mapping.

## Alternatives considered

Hibernate schema generation violates the Flyway contract. Loading data from the
production migration location would populate every environment. A startup runner
would duplicate Flyway's migration lifecycle. Broad JpaRepository inheritance
would expose unbounded read operations that are not needed for this ticket.

## Consequences

OAuth tables remain pending TICKET-0102. Production bootstraps an empty identity
schema, while development/test databases contain the four reference roles. Each
environment needs its own database and migration history. Future catalog changes
must use new migrations. HTTP access rules remain unchanged; entities are internal.

See [database](../database.md) for migration paths and operational guidance.
