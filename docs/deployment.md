# Deployment
The supplied Compose file is explicitly local development (docker,dev and bff).
See [development](development.md#complete-local-platform) for exact commands.

## Services
PostgreSQL 18.6 hosts auth_db/task_db/kpi_db on a named volume with independent
runtime/migration roles. Auth binds localhost:9000. Task/KPI remain internal on
8082/8083. Nginx binds localhost:8080/8081 for static apps and BFF/OAuth proxies.

Java image builds run unit tests; Maven verify separately runs integration tests.
Images use UID/GID 10001, JRE-only runtimes, dropped capabilities, no new privileges,
read-only filesystems and /tmp tmpfs. Nginx uses unprivileged ports and /tmp paths.
The PostgreSQL entrypoint drops privileges and owns its persistent volume; operator
credentials are never passed to apps.

## Production
Provision databases, roles/users and registered browser clients deliberately.
Do not enable dev/test seeds or deploy the development Compose file unchanged.
Configure prod,bff where needed, issuer/public redirects, internal provider URLs
and all secrets. Use TLS and BFF_COOKIE_SECURE=true. HSTS applies to secure
requests; configure a trusted TLS proxy rather than accepting arbitrary forwarding
headers. Replace localhost callbacks with explicitly registered public callbacks.

Mount signing keys read-only for container UID/GID 10001. Rotation requires a
planned multi-key JWKS procedure; replacing the only key is not a rotation system.
Restrict OAuth authorization storage, encrypted backups and migration credentials.
Explicit machine-client provisioning uses the Flyway connection, retaining
read-only registered-client runtime permissions.

Auth limits and BFF sessions are per-instance. Horizontal scaling needs a shared
ingress/Redis limiter and shared sessions or routing affinity. Task keys and KPI
leases are already database-backed.

## Operations
Compose ps, health checks and backend per-service JSON logs diagnose startup failures.
Nginx request/access errors are not logged because OAuth callback URLs contain codes. Missing secrets/keys
fail startup; bad JWT issuer/audience returns 401; missing browser CSRF returns
403; stale versions and changed key payloads return 409. KPI returns 503 before
its first snapshot, then serves stale complete data during an outage.

Keep generated secrets paired with the database volume. Initialization only runs
on a new volume. Compose down preserves data; removing the named volume destroys
users, grants, tasks and history. Metrics require authentication; health/info are public.
