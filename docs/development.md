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

## Registration verification (TICKET-0104)

Auth-server `test` now includes Argon2id salt/matching tests and Mockito service
validation/failure tests. `clean verify` additionally runs PostgreSQL registration,
concurrent duplicate, and captured-log integration coverage alongside the existing
suite. No extra service, profile, or environment variables are needed: integration
tests explicitly activate `test` and supply disposable database credentials.

In a restricted environment with a read-only default Maven cache, use:

```sh
mvn -Dmaven.repo.local=/tmp/ticket-0104-m2 -f enterprise-platform/auth-server/pom.xml clean verify
```

Docker socket access and Maven Central access are required. This ticket adds
Bouncy Castle to auth-server only; the other independent project commands above
remain unchanged.

On 2026-09-09, all three `clean verify` commands passed using
`-Dmaven.repo.local=/tmp/ticket-0104-m2`: auth-server ran 5 unit tests and 8
integration tests with zero failures/errors/skips; Task and KPI still have no
application tests. The initial plain Maven invocation was blocked by the read-only
default cache; the temporary cache and approved Docker/network access resolved it.
The actual Surefire classpath confirms Security 7.1.1, Bouncy Castle 1.85.2,
JUnit Jupiter/Platform 6.0.3, and Mockito 5.23.0. `git diff --check` also passed.

## OIDC local walkthrough (TICKET-0105)

Prerequisites: Java 25+, Maven, Docker for tests, and a provisioned PostgreSQL
`auth_db` with separate runtime/migration logins as described in the
[auth-server README](../enterprise-platform/auth-server/README.md). Manual local
material generation below also uses `unzip` and a POSIX filesystem. Run these
commands from the repository root. Integration tests generate ephemeral keys and
client secrets automatically and require no external signing configuration.

```sh
mvn -f enterprise-platform/auth-server/pom.xml clean verify
unzip -q -o enterprise-platform/auth-server/target/auth-server-0.0.1-SNAPSHOT.jar 'BOOT-INF/lib/*' -d enterprise-platform/auth-server/target/auth-tools
java --class-path 'enterprise-platform/auth-server/target/auth-tools/BOOT-INF/lib/*' scripts/com/parvez/tools/PrepareDevelopment.java
```

The explicit utility creates `.local/auth/` (0700) with 0600 files: a generated
3072-bit RSA pair, signing/encoded-client-secret environment values, a private HTTP
Client environment for both clients, and an atomic development employee SQL script.
It refuses to overwrite an existing directory. Keep these files across restarts;
V2.1 runs once, so regenerated client credentials would not match the stored hashes.
It prints paths only, never credentials. `.local/`, PEM files and HTTP private
environments/history are ignored by Git.

Fill `enterprise-platform/.env` from `.env.example` with database credentials,
then run:

```sh
set -a
. enterprise-platform/.env
set +a
. .local/auth/authorization.env
java -jar enterprise-platform/auth-server/target/auth-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,dev
```

After startup migrates the fresh development database, open another terminal and
provision the generated employee using your runtime database login (`psql` prompts
for the database password; substitute the host/port/user if changed):

```sh
psql -h localhost -p 5432 -U auth_app -d auth_db -W -v ON_ERROR_STOP=1 -f .local/auth/bootstrap-user.sql
cp .local/auth/http-client.private.env.json requests/http-client.private.env.json
```

Open [requests/auth.http](../requests/auth.http) in IntelliJ, select environment
`task`, and run all ten requests in order. Repeat with `kpi`. The HTTP Client keeps
session cookies, captures login CSRF, generates fresh S256 PKCE/state/nonce values,
exchanges the code, fetches public keys/UserInfo, rotates the refresh token, and
asserts rejection of replayed credentials. Callbacks need not be running because
redirect following is disabled for authorization. Keep secret values in the private
file; do not enable verbose HTTP history or copy secrets into browser JavaScript.

Alternatively, install the official [JetBrains HTTP Client CLI](https://www.jetbrains.com/help/idea/http-client-cli.html)
into a local tools directory and run:

```sh
ijhttp --no-progress --private-env-file requests/http-client.private.env.json --env task requests/auth.http
ijhttp --no-progress --private-env-file requests/http-client.private.env.json --env kpi requests/auth.http
```

`AuthorizationCodeFlowIT` independently verifies RSA signatures against the served
JWKS, issuer, audience, expiry, nonce, UUID subjects, role/permission claims,
UserInfo, database persistence, rotation, replay protection, disabled-user refresh
rejection, invalid client/redirect/PKCE rejection, and absence of secret values in
application logs. To focus on that integration test (also runs unit tests):

```sh
mvn -f enterprise-platform/auth-server/pom.xml verify -Dit.test=AuthorizationCodeFlowIT
```

### Required deployment configuration

Every profile requires `AUTH_ISSUER`, `AUTH_RSA_PRIVATE_KEY`, `AUTH_RSA_PUBLIC_KEY`,
and `AUTH_RSA_KEY_ID` in addition to the existing database settings. Key locations
are Spring resource URIs (for example `file:/run/secrets/auth-private.pem`); the
private key must be PKCS8 PEM and the public key X509 PEM. Use a stable HTTPS issuer
and at least 2048-bit matching RSA keys; loopback HTTP is accepted for local work.
The setup utility supplies all four for a local port-9000 instance.

Only explicit `dev`/`test` activation requires `AUTH_TASK_CLIENT_SECRET_HASH` and
`AUTH_KPI_CLIENT_SECRET_HASH` (prefixed Argon2id encodings). `prod` excludes the seed
location even if combined with `dev` or `test`. Normal `local`/`docker` does not
seed clients or reference roles. Production must provision its own clients,
redirects, roles and users using approved database migrations/provisioning.

### TICKET-0105 verification results

On 2026-09-09, the following command passed:

```sh
mvn -Dmaven.repo.local=/tmp/ticket-0104-m2 -f enterprise-platform/auth-server/pom.xml clean verify
```

Results: 7 unit tests and 12
integration tests, zero failures/errors/skips, on Java 25.0.4 and PostgreSQL 18.6.
JUnit Jupiter/Platform remain aligned at 6.0.3. No dependency versions changed.
Initial flow tests exposed login-page entry-point configuration, missing UserInfo
bearer processing, and immutable-list JSON allowlisting issues; all were fixed
before final verification.

The packaged JAR was separately started with `local,dev`, generated 3072-bit keys,
and a fresh PostgreSQL database using distinct non-superuser runtime/migration
logins. JetBrains HTTP Client CLI 2026.1 (build 261.25134.95) executed this repository's
`requests/auth.http` for both environments: 10/10 requests passed per client.
Generated passwords/client secrets were absent from application logs. Temporary
application/container processes were removed after verification. `git diff --check`
passed. Task and KPI remain unchanged skeletons without application tests.
