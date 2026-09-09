# Development

## Prerequisites

Use Java 25 or newer and Maven (no Maven wrapper is present yet). Confirm with
`java -version` and `mvn -version`; Maven must use the intended JDK.
Docker and Docker Compose will be needed when services and Testcontainers tests
are introduced. The current skeleton needs no running containers, profiles, or
environment variables.

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

These are empty JAR projects: no sources/tests and empty JAR warnings are expected.
Successful verification checks the scaffold build lifecycle, not application
behavior. Add meaningful tests with application implementation in later tickets.

## Frontend and Compose placeholders

Open `enterprise-platform/frontend/task-management-ui/index.html` or
`enterprise-platform/frontend/kpi-ui/index.html` in a browser. Neither needs Node
or a frontend build step yet.

`enterprise-platform/docker-compose.yml` intentionally contains `services: {}`.
No containers can be started yet. The environment template contains comments only;
add variables and document them when the Compose services are implemented.

Keep local `.env` files private; `.env.example` is tracked. Build output, IDE
metadata, and `node_modules/` are ignored by Git.

## Agreed implementation baseline (TICKET-0002)

Follow [architecture](architecture.md) for verified Spring versions and the
approved Boot-managed JUnit 6 baseline. Verify the resolved test dependency graph
and test execution when bootstrapping the applications.
Future local Compose setup uses one PostgreSQL container with three databases;
KPI polls Task over REST. Kafka, RabbitMQ, and Redis are not required.
These are design decisions only: the existing build commands still verify empty
skeletons, and no new profiles, environment variables, or running services were
introduced in this ticket.
