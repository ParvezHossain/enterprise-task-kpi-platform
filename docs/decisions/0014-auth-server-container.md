# ADR 0014: Standalone auth-server container

Status: Accepted — TICKET-0110

## Context and options

The auth server needs an independently buildable image with external PostgreSQL
and signing keys. Options included shipping a JDK, a minimal JRE, or a custom
jlink/distroless runtime. A source build or a host-built JAR could supply the app.

## Decision

Build from source with the official Maven 3.9.16/Temurin 25 Alpine image; copy only
the executable JAR into the official Temurin 25.0.4+7 Alpine 3.24 JRE.
Pin both base images by manifest-list digest, retaining amd64/arm64 support.
Use the stock JRE to preserve library compatibility without maintaining a custom
Java module set. Use BusyBox wget already in Alpine for HTTP health checks.

Run as UID/GID 10001 with a root-owned, read-only JAR and Java as PID 1.
Supply database credentials and signing keys at runtime; the build context
allowlist excludes host artifacts and local secret material. Support a read-only
root filesystem with writable /tmp. Default to the docker profile; deployments
can explicitly combine docker,prod to enforce seed isolation.

Run Maven package including unit tests in the builder. Run clean verify with
Docker on the host/CI as the mandatory integration gate; never mount a Docker
socket into the image builder or skip tests to claim verification.
A disposable smoke utility validates the actual built image against PostgreSQL
with separate runtime/migration credentials.

## Consequences and compatibility

No Maven, compiler, source tree or build cache enters the runtime stage.
Base digest updates require rebuilding and repeating Maven verification and the
container smoke check. The health probe includes database connectivity and uses
AUTH_SERVER_PORT; changing the management endpoint path/port requires a matching
health-check override. Full platform Compose orchestration remains a later ticket.
See architecture documentation for official image/version evidence.
