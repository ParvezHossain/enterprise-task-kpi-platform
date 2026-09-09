# Auth Server

Java 25 / Spring Boot 4 bootstrap in `com.parvez.auth`. Includes Security,
Authorization Server, JPA, PostgreSQL, Flyway, Actuator, validation, and springdoc.
Only `GET /actuator/health` is public. Users, OAuth clients, login/token flows,
and domain migrations are introduced in later tickets.

## Verify

From this directory, with Java 25+, Maven 3.6.3+, and Docker available:

```sh
mvn clean verify
```

Failsafe runs `AuthServerApplicationIT` using JUnit 6 and a disposable PostgreSQL
18.6 container. It starts a real HTTP server and checks public health and denied
access to other endpoints. Docker is required; tests are not silently skipped.
Testcontainers provides isolated test credentials; no local `.env` is needed.

## Database prerequisite

Provide an empty PostgreSQL database named `auth_db`, a non-superuser runtime role
`auth_app`, and a separate migration role `auth_migrator`. For example, connect as
a PostgreSQL administrator with `psql` and run this once on a new development DB:

```sql
CREATE ROLE auth_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE auth_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
\password auth_migrator
\password auth_app
CREATE DATABASE auth_db OWNER auth_migrator;
REVOKE CONNECT ON DATABASE auth_db FROM PUBLIC;
GRANT CONNECT ON DATABASE auth_db TO auth_migrator, auth_app;
\connect auth_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO auth_app;
```

The `\password` commands prompt for passwords. This provisions roles and the
empty database only. Application schema changes and runtime table/sequence grants
belong to Flyway migrations starting in TICKET-0102. Hibernate uses `validate`;
Flyway runs normally with no domain migrations yet.

## Run locally

Copy `../.env.example` to `../.env`, fill in both database passwords, then export
its variables into your shell. Maven and Java do not automatically load `.env`:

```sh
set -a
. ../.env
set +a
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

In another terminal:

```sh
curl --fail --include http://localhost:9000/actuator/health
```

Expected: HTTP 200 with `"status":"UP"` (Boot may also list health group names).
No health components or database details
are exposed anonymously. A database outage makes the database health indicator
fail. Other endpoints return 401 for anonymous requests; Swagger is installed but
not exposed publicly by the bootstrap security policy.

After `mvn clean verify`, the executable JAR can also be started with:

```sh
java -jar target/auth-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## Configuration

| Variable | Purpose / default |
| --- | --- |
| `AUTH_DB_USERNAME` | Required runtime database login. |
| `AUTH_DB_PASSWORD` | Required runtime password. |
| `AUTH_DB_MIGRATION_USERNAME` | Required Flyway login. |
| `AUTH_DB_MIGRATION_PASSWORD` | Required Flyway password. |
| `AUTH_DB_URL` | Optional JDBC override; local defaults to `jdbc:postgresql://localhost:5432/auth_db`, docker to `jdbc:postgresql://postgres:5432/auth_db`. |
| `AUTH_SERVER_PORT` | HTTP port, default `9000`. |
| `SPRING_PROFILES_ACTIVE` | `local` (default) or `docker`. |

For containers, export the same credentials and set `SPRING_PROFILES_ACTIVE=docker`.
The docker profile expects database DNS name `postgres` on its container network.
The platform Compose file is still a placeholder; a service image and full Compose
orchestration belong to later tickets. Never commit the populated `.env` file.
