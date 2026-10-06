# ADR 0019: Publish complete KPI generations
Status: Accepted.

## Context and options
Dashboards need bounded synchronization without accessing another database.
Options were synchronous calls, a broker, or scheduled REST pull.

## Decision
KPI authenticates as kpi-sync with client credentials and task.metrics.read.
Task validates subject and scope. UUID keyset pages contain at most 500 metric
DTOs, without descriptions. A sweep captures an upper ID and is limited to
10,000 pages/five minutes. Connection/response timeouts are two/five seconds.
A database lease prevents overlap. Publish only after all pages succeed; failure
preserves the previous generation. Cleanup deletes at most 500 inactive rows.
Repeatable-read transactions see one generation.

Current Team Leader access is checked against Task leadership data. Company and
broad rankings require Admin/PM; Team Leaders need an authorized team. Employee
queries use the authenticated subject. SQL computes aggregates and ranking scores;
employee UUID breaks ties. No aggregate cache, consistent with ADR 0006.

## Consequences and API changes
Responses include dataAsOf, lastSuccessfulSyncAt and stale after five minutes.
Before the first successful sweep return 503; outages may serve stale complete data.
Auth provisions an explicitly configured machine secret hash through the Flyway
migration connection, preserving read-only registered-client runtime access.
No direct Task database access.
