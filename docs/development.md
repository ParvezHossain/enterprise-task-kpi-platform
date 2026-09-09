# Development

## Prerequisites

Use Java 25 or newer and Maven (no Maven wrapper is present yet). Confirm with
`java -version` and `mvn -version`; Maven must use the intended JDK.
Docker is required for auth-server Testcontainers integration tests. For manual
startup, provision PostgreSQL and export the credentials documented in the
[auth-server README](../enterprise-platform/auth-server/README.md). Docker Compose
orchestration is introduced in later tickets.

## Build and test

From the repository root:

| Module | Complete verification | Unit tests during development |
| --- | --- | --- |
| Auth server | `mvn -f enterprise-platform/auth-server/pom.xml clean verify` | `mvn -f enterprise-platform/auth-server/pom.xml test` |
| Task management | `mvn -f enterprise-platform/task-management/pom.xml clean verify` | `mvn -f enterprise-platform/task-management/pom.xml test` |
| KPI service | `mvn -f enterprise-platform/kpi-service/pom.xml clean verify` | `mvn -f enterprise-platform/kpi-service/pom.xml test` |

Alternatively, run `mvn clean verify` inside each backend directory. No root POM
or Maven reactor exists. Run all three commands for shared build changes.
Maven may need network access to download lifecycle plugins on the first build.

Auth-server verification runs real HTTP integration tests against disposable
PostgreSQL using JUnit 6 and Failsafe. Task and KPI remain empty JAR projects with
no sources/tests. Their successful builds check only the scaffold lifecycle.

## Frontend and Compose placeholders

Open `enterprise-platform/frontend/task-management-ui/index.html` or
`enterprise-platform/frontend/kpi-ui/index.html` in a browser. Neither needs Node
or a frontend build step yet.

`enterprise-platform/docker-compose.yml` intentionally contains `services: {}`.
No Compose services can be started yet. The environment template now contains
auth-server variables; it must be exported explicitly for standalone startup.

Keep local `.env` files private; `.env.example` is tracked. Build output, IDE
metadata, and `node_modules/` are ignored by Git.

## Agreed implementation baseline (TICKET-0002)

Follow [architecture](architecture.md) for verified Spring versions and the
approved Boot-managed JUnit 6 baseline. Verify the resolved test dependency graph
and test execution when bootstrapping the applications.
Future local Compose setup uses one PostgreSQL container with three databases;
KPI polls Task over REST. Kafka, RabbitMQ, and Redis are not required.
TICKET-0101 implements the auth-server baseline with `local` and `docker` profiles.
See its README for database provisioning, environment variables, executable JAR
startup, and the `curl` health check.

## TICKET-0101 verification

On 2026-09-09, auth-server `clean verify` passed on Java 25.0.4 with two JUnit 6
integration tests, zero failures/errors/skips, and Testcontainers PostgreSQL 18.6.
In the restricted agent environment, Maven used
`-Dmaven.repo.local=/tmp/ticket-0001-m2` for a writable dependency cache.

The packaged executable JAR was also started under both `local` and `docker`
profiles against a disposable PostgreSQL database, with `AUTH_DB_URL` pointing to
its temporary host port. Both returned HTTP 200 from `/actuator/health` with
`"status":"UP"`, and HTTP 401 from `/v3/api-docs`. This checks both profile
configurations on the host; container-network orchestration is not implemented yet.
The smoke check used separate non-superuser runtime and migration logins and
confirmed Flyway's history table belongs to the migration role. Temporary app
processes and the database container were removed after verification.

Packaged library versions and the actual test classpath matched the documented
BOM baseline. No domain migrations exist yet, so Flyway's no-migrations warning
is expected until TICKET-0102.

## Identity repository verification (TICKET-0103 in progress)

`mvn clean verify` passed with six tests after adding the identity schema,
repositories, four-role development seed, and production seed-isolation coverage.
The same temporary Maven cache described above was used. Permission seed coverage
and the completion commit await the exact master-spec permission catalog.

Use `local,dev` to enable the separate development seed location. Normal `local`
and `docker` startup applies only schema migrations; `prod` overrides accidental
`dev`/`test` activation. See [database](database.md) for migration history isolation.
