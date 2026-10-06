# kpi-service
Java 25 / Boot 4.1.1; independent Maven project under com.parvez.

From the repository root:

    mvn -f enterprise-platform/kpi-service/pom.xml clean verify
    docker build -t kpi-service:local enterprise-platform/kpi-service

Docker is required for PostgreSQL Testcontainers. Tests generate keys, credentials
and controlled HTTP dependencies. Verify runs unit and Failsafe integration tests;
image builds run unit tests only.

Configure KPI_DB_URL, KPI_DB_USERNAME, KPI_DB_PASSWORD,
KPI_DB_MIGRATION_USERNAME, KPI_DB_MIGRATION_PASSWORD,
KPI_AUTH_ISSUER and KPI_AUTH_JWK_SET_URI. Port 8083 can be changed with
KPI_SERVER_PORT. Flyway owns schema; Hibernate validates it.

Browser sessions require explicit bff profile, BFF_CLIENT_SECRET, AUTH_ISSUER,
BFF_AUTH_INTERNAL_URL and BFF_REDIRECT_URI. TLS deployments set BFF_COOKIE_SECURE=true.
Bearer endpoints remain stateless.

KPI needs KPI_SYNC_SECRET, KPI_TOKEN_URL and KPI_TASK_URL. KPI_SYNC_INTERVAL_MS defaults to 60000; KPI_SYNC_ENABLED=false disables scheduled pulls in controlled tests. No aggregate cache.

Health/info are public; metrics/Prometheus are authenticated. Containers run as
UID/GID 10001. See [development](../../docs/development.md#complete-local-platform),
[API](../../docs/api.md), [security](../../docs/security.md) and
[deployment](../../docs/deployment.md).
