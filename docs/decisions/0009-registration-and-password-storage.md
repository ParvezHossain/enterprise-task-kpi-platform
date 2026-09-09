# ADR 0009: Registration and Argon2id password storage

- Status: accepted
- Ticket: TICKET-0104
- Date: 2026-09-09

## Context and options

Registration needs salted, adaptive password hashes and must not expose credentials
through DTOs, validation failures, persistence failures, or application logs.
Options were Spring Security's Argon2id encoder or its default delegating encoder
(which encodes with bcrypt). Argon2id is supported directly in Security 7.1.1,
so no fallback is necessary. Bouncy Castle is its required Java implementation;
no native library or global JCA provider registration is needed.

## Decision

Use `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` and explicitly pin
`org.bouncycastle:bcprov-jdk18on:1.85.2`, which Boot 4.1.1 does not manage.
The preset name denotes parameter history; it remains available in Security 7.1.1.
Hashes contain Argon2id version/cost metadata, a random 16-byte salt, and a
32-byte digest (16 MiB memory, two iterations, parallelism one). Benchmark and tune
costs on deployment hardware before public authentication rollout.

`UserService.register` validates a dedicated request, hashes before acquiring a
transaction, and inserts one user with only EMPLOYEE. Role selection is never
client-controlled. A missing EMPLOYEE role fails closed; production provisioning
must supply approved reference data. No privileged provisioning API is introduced.
The service returns an immutable DTO with identity and role names only.

Use a parameterized PostgreSQL `INSERT ... ON CONFLICT (email) DO NOTHING`, then
load the inserted entity and assign its role within the same transaction. This
makes duplicate detection safe under concurrent registration without generating
SQL exceptions containing submitted data. A preliminary existence check followed
by an ordinary insert was rejected because it races. No migration is needed.

Validation and persistence exceptions expose fixed reason codes, with no rejected
values or original database causes. The transaction template includes commit-time
failures when this service owns the transaction. Requests redact `toString()`.
Hibernate 7 bind/extract/error/warning logging is disabled because it can expose
hashes and row contents. Operators must preserve these credential-sensitive logger
settings and avoid request/body or security-context logging.

## Consequences and compatibility

Bouncy Castle adds a runtime dependency and adaptive hashing consumes CPU/memory.
The native insert is deliberately PostgreSQL-specific. Registration has no HTTP
endpoint and issues no tokens; the existing deny-by-default filter chain remains.
Future HTTP handlers must map the safe service failures without serializing
validation internals, and must enforce their authorization policy server-side.
Login and token handling need their own log-leak regression coverage when added.

Compatibility evidence and verified dependency versions are recorded in
[architecture](../architecture.md). The registration integration test scans captured
application output on success, duplicate/invalid input, and a forced database
CHECK failure, with password and token canaries in the invocation/context.
