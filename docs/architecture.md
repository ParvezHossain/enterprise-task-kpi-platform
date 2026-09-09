# Architecture

## Repository skeleton (TICKET-0001)

Application projects live under `enterprise-platform/`. The repository-level
`docs/` directory remains the canonical location for architectural and development
documentation; `enterprise-platform/docs/README.md` links here.

| Project | Maven coordinates | Future Java package | Responsibility |
| --- | --- | --- | --- |
| `enterprise-platform/auth-server` | `com.parvez:auth-server` | `com.parvez.auth` | Authentication and authorization server |
| `enterprise-platform/task-management` | `com.parvez:task-management` | `com.parvez.task` | Task management |
| `enterprise-platform/kpi-service` | `com.parvez:kpi-service` | `com.parvez.kpi` | KPI service |

All three are independent, parent-less Maven JAR projects targeting Java 25.
There is no shared parent or aggregator. TICKET-0101 adds the auth-server
application and dependencies; Task and KPI remain empty skeletons.
See [ADR 0001](decisions/0001-independent-service-skeletons.md).

## Core decisions (TICKET-0002)

Status: Accepted design for subsequent implementation tickets. Verified against
live official documentation on 2026-09-09. TICKET-0002 documented the decisions;
TICKET-0101 applies the auth-server baseline. Compose remains a placeholder. The repository contract now
uses Boot-managed JUnit 6, as approved by the user.

| Decision | Chosen | Alternatives considered | Why |
| --- | --- | --- | --- |
| Spring baseline | Java 25; Spring Boot **4.1.1**, Spring Framework **7.0.9**, Spring Security **7.1.1**, Authorization Server module **7.1.1** | Boot 3/Security 6; independently pinned Spring components; snapshots | Meets the repository minimums and uses the compatible versions published in Boot's dependency management. |
| Maven dependency management | Import `spring-boot-dependencies:4.1.1` in each independent POM when bootstrapping; pin build plugins separately | Shared parent; unmanaged dependency versions | Preserves ADR 0001 and keeps Spring libraries aligned. |
| Test framework | Boot-managed JUnit 6 (Jupiter/Platform 6.0.3) | Override Boot dependency management with an older JUnit generation | Aligns the approved contract with the selected Boot baseline. |
| Local PostgreSQL topology | One PostgreSQL container, three databases and separate service credentials | Three containers; one shared database/schema | Lower local resource/setup cost while retaining database ownership boundaries. |
| KPI↔Task integration | Scheduled, bounded REST pull of task metric DTOs into KPI's PostgreSQL read model; initial interval 60 seconds | Kafka/RabbitMQ events; direct database access; fetching every task on each dashboard request | Simple to inspect and test, supports SQL aggregates in KPI, and keeps dashboards available during short Task outages. No message broker. |
| Approve/assign/close idempotency | Required `Idempotency-Key`, durable Task database record committed with the transition and audit event | In-memory map; Redis; optimistic locking alone | Handles retries across restarts/instances without another service; locking alone cannot replay a lost response. |
| KPI aggregate caching | None initially; SQL aggregates on the last complete local read model | Immediate Caffeine or Redis caching | Avoids a second source of staleness before measurements show a need. |

Decision records: [Spring baseline](decisions/0002-spring-baseline.md),
[local databases](decisions/0003-local-databases.md),
[REST pull](decisions/0004-kpi-rest-pull.md),
[idempotency](decisions/0005-command-idempotency.md), and
[caching](decisions/0006-kpi-caching.md).

## Verified versions and current APIs

The [Boot dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
currently identify Boot 4.1.1, Framework 7.0.9, Security 7.1.1, and
`org.springframework.security:spring-security-oauth2-authorization-server:7.1.1`.
The [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
support Java 25 (the documented range extends through Java 26), and require Maven
3.6.3+. These pages are rolling documentation: recheck their displayed release
and the selected release BOM before adding or upgrading dependencies.

Spring Authorization Server is [part of Spring Security from version 7](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/).
Use Boot's `spring-boot-starter-security-oauth2-authorization-server`, managed by
the selected BOM. Do not combine standalone Authorization Server 1.x with this
Security 7 baseline. This is the current distribution of the required stack item,
not a replacement authorization implementation.

Follow the [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
for modular starters and changed package names. Use the MVC and Security OAuth2
starters appropriate to Boot 4, `jakarta.*` persistence/validation APIs, and the
current `SecurityFilterChain`/lambda configuration. Do not copy Boot 2/3 security
configuration. For the blocking Task client, use `RestClient`;
[Framework 7 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
deprecate `RestTemplate` in documentation. Use Jackson 3 conventions for new code.

Spring Data JPA, Hibernate, PostgreSQL JDBC, Flyway, Actuator, Micrometer, Mockito,
and Testcontainers remain required. Use BOM-managed releases when bootstrapping,
verify resolved versions, and configure PostgreSQL-specific Flyway support.
[springdoc's compatibility guidance](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)
places Boot 4 on springdoc 3.x; pin and verify its exact compatible release in the
bootstrap ticket. The auth-server pins and verification are recorded below.

### Test stack: JUnit 6

Use Boot-managed JUnit Jupiter/Platform **6.0.3** with the selected Boot 4.1.1
baseline, as listed in the dependency coordinates above. The user approved
updating the repository contract to **JUnit 6**, resolving the earlier mismatch.
Use the BOM to align Jupiter and Platform; do not override them independently.

Bootstrap tickets must configure compatible Surefire/Failsafe plugins and verify
Spring context, Mockito, and Testcontainers tests on Java 25. This documentation
decision is now exercised by the auth-server JUnit 6 integration tests using
Spring context and Testcontainers. Mockito is supplied by the test starter; no
mocking is needed for the real database/HTTP bootstrap checks.

## Local database boundaries

Use one local Compose service named `postgres`, with databases `auth_db`,
`task_db`, and `kpi_db`. Each service has a distinct non-superuser runtime login
that can connect only to its own database; revoke default public connect/access
privileges and grant only the intended access. Use a separate migration identity
per database for Flyway DDL. Application credentials must never be the cluster
administrator credentials.

Provision roles and empty databases in local container initialization; all
application tables, indexes, and data migrations belong to each service's Flyway
history. Set Hibernate `ddl-auto=validate`. No cross-database queries, foreign data
wrappers, shared entities, or KPI access to Task JDBC credentials. A single named
volume is sufficient locally; a container failure affects all three databases.
Three instances remain a deployment option if isolation or independent scaling is
needed later. Initialization must account for existing volumes; restarting a
container is not a schema migration mechanism.

## REST pull and KPI consistency

Task owns authoritative task data. KPI owns a derived metric projection in
`kpi_db`, containing task ID/version, employee/team/project IDs, status, priority,
and timestamps needed for completion, due-date, duration, and trend queries.
Include lifecycle timestamps/history fields needed by the agreed KPI semantics;
Task descriptions and other unnecessary personal data do not belong in the feed.
DTOs are explicit API contracts, never serialized JPA entities.

TICKET-0302 will add a dedicated internal read endpoint to Task and a `RestClient`
adapter in KPI. Authenticate KPI with a confidential client-credentials client,
a Task-audience token, and a narrowly scoped `task.metrics.read` permission.
This machine identity cannot mutate tasks. The feed is not accessible to browser
clients. KPI independently enforces current user/role/ownership/team access for
every dashboard query; possession of copied metric rows grants no user access.
Do not treat old projection membership as authoritative authorization data.

Start with a full sweep every 60 seconds, using stable task-ID keyset pagination,
a server-enforced maximum of 500 rows per page, and a captured upper task ID so a
run does not grow indefinitely. Process one page at a time into a staging
generation in KPI's database. Limit a run to 10,000 pages and 5 minutes; exceeding
either is a failed refresh, never a successful partial refresh. Prevent overlapping
runs with a database-backed lease if multiple KPI instances are used.

Use a 2-second connection timeout and a 5-second response timeout. Do not retry
inside a failed run initially; the next scheduled run retries the refresh. Publish
the generation only after every page succeeds, by atomically changing the active
generation in a transaction. Retain the previous complete generation while a
refresh is in progress or fails. Clean old/staging generations in bounded batches
without deleting data still used by active queries. Each aggregate request reads
one active generation within a consistent database transaction.

This is eventual consistency: a sweep observes changes at different times, not a
point-in-time snapshot of Task. Stable pagination prevents offset drift; later
sweeps pick up concurrent changes and new tasks. Replacing a complete generation
also removes tasks no longer returned by the feed. Audit history must be retained
according to Task's domain rules; snapshot removal is not audit deletion.

Return `dataAsOf` (successful sweep start), `lastSuccessfulSyncAt`, and a `stale`
flag when the data age exceeds 5 minutes. Serve the last complete snapshot during
a Task outage and expose refresh failure/age via metrics. Before the first
successful refresh, return 503 Problem Details, not misleading zero KPIs. A
successful empty sweep legitimately produces zeros. TICKET-0303 computes
COUNT/SUM/AVG/GROUP BY in KPI's database, with capped pagination for rankings and
other list results; do not sum loaded entities in Java.

If measured data volume makes full sweeps too expensive, add a durable incremental
change cursor plus deletion records and periodic reconciliation over REST first.
A broker requires a new decision and a demonstrated need.

## Durable command idempotency

Require an opaque `Idempotency-Key` of 1–128 printable non-whitespace ASCII
characters on approve, assign, and close. Missing/invalid keys return 400 Problem
Details. Scope the unique record to authenticated issuer/subject, OAuth client,
HTTP method, canonical resource path (including task ID), and key; include tenant
identity if tenancy is added. Store a hash of canonical validated request fields,
including the expected task version, to detect a changed payload.

Authenticate and authorize every request, including replays. In one Task database
transaction, claim the key via a unique insert, enforce the state machine and
optimistic task version, append the audit event, and store the successful response
status, body, and relevant headers. Commit them together. Do not store credentials
or tokens in the record. Rollback removes the key claim on validation/domain errors
or transaction failure; failed operations are not cached initially.

A committed duplicate with the same request hash replays the original response
without rechecking the now-changed task version or executing the transition again.
Recheck current access before exposing a stored response. A different hash returns
409 Problem Details. Concurrent identical claims wait only for a bounded database
lock timeout; then either replay the committed result, or return 409 with
`Retry-After: 1` so the caller retries with the same key. Use conflict-safe SQL or a
fresh transaction to read a winner; do not query inside a transaction already
aborted by a uniqueness error. Separate keys racing for the same task still use
optimistic locking, with one winner and one 409.

Retain successful keys for at least 24 hours from commit and clean expired rows
in bounded batches. Clients must retry within this window; after expiry the key
may be treated as a new request and domain rules apply. This is a bounded replay
guarantee, not unlimited exactly-once delivery. Add the table/indexes using Flyway.
TICKET-0209 must test replay after a lost response/restart, payload mismatch,
concurrent duplicate keys, separate-key version races, rollback/retry, expiry, and
replay after access revocation.

## Aggregate caching

Do not add a cache library, Redis, or `@Cacheable` initially. The persistent task
projection is the integration read model, not a cache of aggregate responses.
Measure SQL latency and query counts with Micrometer and tune indexes first.

If repeated aggregate queries miss an agreed latency target, add a bounded local
Caffeine cache behind Spring's cache abstraction: initial maximum 1,000 entries,
30-second TTL, and keys containing the active generation, metric, normalized
filters/time range, pagination, and complete authorization scope. Always authorize
before cache access. A new generation must never reuse old generation results;
policy/membership changes invalidate relevant entries or change the authorization
scope version. Do not cache failed responses. Verify isolation and freshness with
tests. Consider Redis only if measured multi-instance needs justify operating it.

## Auth-server bootstrap (TICKET-0101)

The auth-server POM imports the Boot 4.1.1 BOM without a parent. Resolution from
[Maven Central's release BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)
confirms Framework 7.0.9, Security/Authorization Server 7.1.1, Hibernate 7.4.5.Final,
Flyway 12.4.0, PostgreSQL JDBC 42.7.13, JUnit 6.0.3, Mockito 5.23.0, and
Testcontainers 2.0.5. springdoc MVC UI is pinned to 3.1.1 using its official
Boot 4 compatibility guidance and
[release metadata](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml).

The compiler plugin is pinned to 3.14.1 (Java 25 compilation), Surefire/Failsafe
to 3.6.0 (JUnit Platform execution), and the Boot plugin to 4.1.1. Plugin releases
were checked against Maven Central; see
[compiler metadata](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-compiler-plugin/maven-metadata.xml)
and [Surefire metadata](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-surefire-plugin/maven-metadata.xml).
Clean/resources/JAR lifecycle plugins retain the previously verified scaffold
versions, now explicitly pinned. Failsafe uses `target/classes` so repackaging the
executable Boot JAR does not hide application classes from integration tests.

The test database image is pinned to `postgres:18.6-alpine`, following the
[PostgreSQL 18 release notes](https://www.postgresql.org/docs/18/release.html).
The local/docker profiles change the default database host and accept explicit
runtime and migration credentials. Database health remains enabled; no H2 or
persistence exclusions are used to make startup pass. The bootstrap originally initialized only Flyway history. TICKET-0103 now adds
the prerequisite identity schema; OAuth schema migrations remain for TICKET-0102.

See [ADR 0007](decisions/0007-auth-bootstrap-security.md) for the bootstrap access
policy and [auth-server README](../enterprise-platform/auth-server/README.md)
for startup instructions. Boot 4-specific changes include the modular Flyway and
Security starters, the `org.springframework.boot.security.autoconfigure` package
for default-user auto-configuration, Jackson 3's `tools.jackson` test API, and
Testcontainers 2's `testcontainers-postgresql` artifact and
`org.testcontainers.postgresql` package.

## Identity persistence (TICKET-0103)

See [ADR 0008](decisions/0008-identity-persistence-and-seed-isolation.md) and
[database documentation](database.md) for the identity schema, repository bounds,
and dev/test-only seed locations. No new dependencies are introduced.
[Spring Data selective repository definitions](https://docs.spring.io/spring-data/jpa/reference/repositories/definition.html)
support exposing only single-record lookups and writes; Flyway remains the sole
schema/data initialization mechanism, following
[Boot database initialization guidance](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## Registration and password storage (TICKET-0104)

[ADR 0009](decisions/0009-registration-and-password-storage.md) defines the service
boundary, fixed EMPLOYEE assignment, duplicate handling, and log protection.
`com.parvez.auth.service` owns registration requests/results and transaction
coordination; `com.parvez.auth.config.PasswordConfiguration` exposes the encoder.

Security 7.1.1's [versioned Argon2PasswordEncoder source](https://github.com/spring-projects/spring-security/blob/7.1.1/crypto/src/main/java/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.java)
confirms Argon2id and the supported `defaultsForSpringSecurity_v5_8()` factory.
[Spring Security password storage guidance](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html)
identifies the Bouncy Castle requirement and recommends deployment-specific cost
tuning. The [Bouncy Castle release/download page](https://www.bouncycastle.org/download/bouncy-castle-java/)
lists the 1.85.2 provider for Java 8 and later. We pin `bcprov-jdk18on:1.85.2`
explicitly because it is absent from [Boot 4.1.1's managed coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html).
Boot-managed Spring and test versions are unchanged. No Security API fallback or
stack downgrade is needed. Hibernate 7 uses `org.hibernate.orm.jdbc.error` and
`org.hibernate.orm.jdbc.warn` instead of the older `SqlExceptionHelper` logger;
application configuration targets the current categories.

## OIDC authorization server (TICKET-0105)

[ADR 0010](decisions/0010-oidc-confidential-pkce-clients.md) selects confidential BFF
clients with required PKCE to support refresh tokens without a custom grant.
No dependency versions changed: Boot 4.1.1, Security/Authorization Server 7.1.1,
Framework 7.0.9 and Bouncy Castle 1.85.2 remain pinned as above.

The [Security 7.1 configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
provides the current DSL, OIDC enablement and JWK-source integration.
[Spring's PKCE guide](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-pkce.html)
explains the public-client refresh restriction and recommends BFFs.
The [7.1.1 JDBC authorization implementation](https://github.com/spring-projects/spring-security/blob/7.1.1/oauth2/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/JdbcOAuth2AuthorizationService.java)
uses Jackson 3 by default. Migration V2 copies the table layouts from that exact
release's packaged SQL resources, applying their PostgreSQL `text`/`timestamptz`
instructions. JDBC claim collections use `ArrayList` to satisfy the mapper's type
allowlist; no broad polymorphic deserialization permissions are enabled.

`config.AuthorizationServerConfig` owns protocol wiring and JWT claims;
`SigningConfiguration` loads/validates external keys and issuer.
`security.IdentityAuthenticationService` maps database identities to security
principals and bounded authority snapshots. `BoundedJdbcAuthorizationService`
adds `LIMIT 2` to the framework's token lookup and rejects multiple matches.
Single-ID and client-ID lookups are bounded by unique constraints.

OAuth schema V2 and dev/test client seed V2.1 supply the missing OAuth persistence
prerequisite from TICKET-0102. V1 and V1.1 remain unchanged. The generated login
page is pulled forward from TICKET-0106 solely to support the full protocol flow.
