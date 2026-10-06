# task-management
Java 25 / Boot 4.1.1; independent Maven project under com.parvez.

From the repository root:

    mvn -f enterprise-platform/task-management/pom.xml clean verify
    docker build -t task-management:local enterprise-platform/task-management

Docker is required for PostgreSQL Testcontainers. Tests generate keys, credentials
and controlled HTTP dependencies. Verify runs unit and Failsafe integration tests;
image builds run unit tests only.

Configure TASK_DB_URL, TASK_DB_USERNAME, TASK_DB_PASSWORD,
TASK_DB_MIGRATION_USERNAME, TASK_DB_MIGRATION_PASSWORD,
TASK_AUTH_ISSUER and TASK_AUTH_JWK_SET_URI. Port 8082 can be changed with
TASK_SERVER_PORT. Flyway owns schema; Hibernate validates it.

Browser sessions require explicit bff profile, BFF_CLIENT_SECRET, AUTH_ISSUER,
BFF_AUTH_INTERNAL_URL and BFF_REDIRECT_URI. TLS deployments set BFF_COOKIE_SECURE=true.
Bearer endpoints remain stateless.

Commands require version and Idempotency-Key on approve/assign/close. TASK_MAX_PAGE_SIZE defaults to 100; explicit CORS origins are configurable.

Health/info are public; metrics/Prometheus are authenticated. Containers run as
UID/GID 10001. See [development](../../docs/development.md#complete-local-platform),
[API](../../docs/api.md), [security](../../docs/security.md) and
[deployment](../../docs/deployment.md).
