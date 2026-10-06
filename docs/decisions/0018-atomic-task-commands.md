# ADR 0018: Atomic commands, retries and audit
Status: Accepted.

## Context and options
A lost response requires a replayable result. Optimistic locking detects races but cannot replay responses. Alternatives were in-memory keys, an external store, or Task database records.

## Decision
Approve/assign/close require printable 1–128 character Idempotency-Key values.
SHA-256 scopes include JWT issuer, subject, OAuth client claims, method and canonical task/action path. The canonical payload includes version and assignee.
A conflict-safe insert, transition, immutable audit insert and serialized response
commit together. Lock waits are limited to two seconds. Matching committed requests
replay the DTO after current authorization; changed payloads return 409. Failed
transactions release claims. Retention is 24 hours; cleanup deletes at most 500 rows.

Synchronous Spring application events populate history inside the command transaction.
Runtime grants allow SELECT/INSERT, never UPDATE/DELETE. Counters increment after commit.

## Consequences and API changes
Existing callers must send keys. Tests cover concurrent identical requests, changed
payloads, rollback/retry, expiry, revoked access, version races and full history.
No dependency changes.
