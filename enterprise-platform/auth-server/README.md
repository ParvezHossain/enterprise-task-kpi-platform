# Auth Server

Java 25 / Spring Boot 4 bootstrap in `com.parvez.auth`. Includes Security,
Authorization Server, JPA, PostgreSQL, Flyway, Actuator, validation, and springdoc.
Provides database-backed login, CSRF-protected session logout, OIDC discovery/UserInfo, authorization code with
S256 PKCE, rotating refresh tokens, and RSA-signed JWTs from configured keys.
Two confidential BFF clients are seeded only for explicit dev/test use. Only these
trusted first-party IDs may skip consent; other clients require it. Standalone
login lands on `/account`; `/logout` offers confirmation before a CSRF-protected
POST. Client token revocation is separate from browser session logout. See the
[complete OIDC walkthrough](../../docs/development.md#oidc-local-walkthrough-ticket-0105),
[HTTP requests](../../requests/auth.http), and [security policy](../../docs/security.md).

## Verify

From this directory, with Java 25+, Maven 3.6.3+, and Docker available:

```sh
mvn clean verify
```

Failsafe runs the real HTTP OIDC flow, registration, repository, health, and
production-seed-isolation tests using JUnit 6 and disposable PostgreSQL 18.6.
Surefire verifies password hashing, validation and signing-key rejection. Docker is required; tests are not silently skipped.
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
empty database only. The V1 Flyway migration creates identity tables and grants runtime DML to
`AUTH_DB_USERNAME`. Hibernate validates identity mappings; V2 creates the OAuth JDBC tables with
restricted runtime grants.

## Run locally

Copy `../.env.example` to `../.env`, fill in both database passwords, then export
its variables into your shell. Supply the required issuer and RSA key configuration
from the table below; the [development utility](../../docs/development.md#oidc-local-walkthrough-ticket-0105)
generates suitable local files. Maven and Java do not automatically load `.env`:

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

For development reference roles, activate `local,dev` instead of `local`. The
`test` profile also enables seeds; `prod` always excludes the seed location.
Neither `local` nor `docker` alone seeds data. Use separate development and
production databases; see the migration guidance linked above.

Expected: HTTP 200 with `"status":"UP"` (Boot may also list health group names).
No health components or database details
are exposed anonymously. A database outage makes the database health indicator
fail. OIDC discovery/JWKS and the generated login form are public. Protocol endpoints
enforce their client/user authentication; unrelated endpoints and Swagger remain
denied. See [API contracts](../../docs/api.md).

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
| `SPRING_PROFILES_ACTIVE` | `local` (default) or `docker`; add `dev` for explicit development seeds. `prod` always excludes seeds. |
| `AUTH_ISSUER` | Required stable issuer, HTTPS except loopback development HTTP. |
| `AUTH_RSA_PRIVATE_KEY` | Required PKCS8 PEM resource URI, such as `file:/run/secrets/auth-private.pem`. |
| `AUTH_RSA_PUBLIC_KEY` | Required matching X509 PEM public-key resource URI. |
| `AUTH_RSA_KEY_ID` | Required stable signing key ID. |
| `AUTH_TASK_CLIENT_SECRET_HASH`, `AUTH_KPI_CLIENT_SECRET_HASH` | Required only for explicit dev/test seeds; independently generated prefixed Argon2id hashes. |

For containers, export the same credentials and set `SPRING_PROFILES_ACTIVE=docker`.
The docker profile expects database DNS name `postgres` on its container network.
The platform Compose file remains a placeholder; use the standalone image below. Never commit the populated `.env` file.

## Observability

Health and info are public. Metrics and Prometheus require the existing login
session and return 401 anonymously. Console logs use JSON with request-local
traceId/requestId; X-Request-ID identifies the request in logs.
See [observability setup and verification](../../docs/development.md#auth-observability-ticket-0108)
and [access policy](../../docs/security.md#observability-access-and-log-policy-ticket-0108).

The [TICKET-0109 suite guide](../../docs/development.md#auth-server-security-suite-ticket-0109)
details token issuance unit tests and PostgreSQL-backed OIDC success,
invalid-client, expired-token, and revoked-refresh-token coverage.

## Build and run the container

From the repository root, with Docker, Java 25+ and Maven:

```sh
mvn -f enterprise-platform/auth-server/pom.xml clean verify
docker build -t auth-server:local enterprise-platform/auth-server
```

The multi-stage build compiles from source and runs unit tests with Maven 3.9.16
and Temurin 25. The final image contains only the application JAR and the Alpine
Temurin 25.0.4 JRE, runs as UID/GID 10001, and starts Java directly as PID 1.
Base images are pinned by digest. The build context allowlists Maven/source files;
host target artifacts, environment files, and local signing keys are excluded.
The Docker build runs `package`; the separate `clean verify` is required for
PostgreSQL Testcontainers integration tests. No Docker socket is mounted into the
builder and no tests are disabled.

For a fully disposable standalone check (also requires Python 3 and OpenSSL):

```sh
python3 scripts/smoke-auth-container.py --image auth-server:local
```

This starts a fresh PostgreSQL container on a private Docker network, provisions
separate runtime/migration roles and temporary RSA keys, starts the image, and
waits up to 180 seconds for its built-in health check. It checks HTTP 200/UP,
UID 10001, absence of Maven/javac, and Flyway ownership while running with a
read-only root filesystem and a writable /tmp. It removes its containers, network,
anonymous database volume, and temporary keys on exit. It never touches an
existing database.

For a persistent standalone deployment, provision the database above on a Docker
network called `auth-platform`, with its container aliased `postgres`, or set
AUTH_DB_URL to a database reachable from that network. Prepare a private Docker
env-file with one literal KEY=value per line (no shell quotes or export prefixes)
using the required variables in the table. Set:

```text
SPRING_PROFILES_ACTIVE=docker,prod
AUTH_RSA_PRIVATE_KEY=file:/run/auth-keys/private.pem
AUTH_RSA_PUBLIC_KEY=file:/run/auth-keys/public.pem
```

Use a stable external AUTH_ISSUER and persist the signing keys. Mount a directory
containing the matching PEM files, readable by UID/GID 10001; provision directory
mode 0700 and files 0400 owned by 10001, or equivalent restricted ACLs. Never
bake secrets into the image. Replace the two absolute host paths below:

```sh
docker run -d --name auth-server --network auth-platform \
  --env-file /absolute/path/auth-container.env \
  --mount type=bind,src=/absolute/path/auth-keys,dst=/run/auth-keys,readonly \
  --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --cap-drop=ALL --security-opt=no-new-privileges \
  -p 127.0.0.1:9000:9000 auth-server:local
docker inspect --format '{{.State.Health.Status}}' auth-server
curl --fail http://localhost:9000/actuator/health
docker logs --tail 50 auth-server
```

Health should become `healthy`, with HTTP 200 and `{"status":"UP"}`.
The image probes /actuator/health every 10 seconds, allows 60 seconds for startup,
and becomes unhealthy after three failures. The probe uses AUTH_SERVER_PORT
(default 9000); if changing that variable, update the container port mapping too.
Health includes database connectivity. Keep the management path and port at
their defaults or override the Docker health check accordingly. Application logs
are JSON on stdout. `docker stop auth-server` sends SIGTERM directly to Java.
