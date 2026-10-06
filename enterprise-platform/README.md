# Enterprise Platform
Three independently built Maven services: [Auth](auth-server/README.md),
[Task](task-management/README.md), [KPI](kpi-service/README.md).
The [frontends](frontend/README.md) share plain HTML/JavaScript, Tailwind and charts.

From the repository root:

    mvn -f enterprise-platform/auth-server/pom.xml clean verify
    mvn -f enterprise-platform/task-management/pom.xml clean verify
    mvn -f enterprise-platform/kpi-service/pom.xml clean verify

Java 25/Maven and running Docker are required. All services have application
tests; KPI is no longer an empty skeleton. There is no root reactor.

Follow [complete local setup](../docs/development.md#complete-local-platform) to
generate private material, build/start Compose and seed local data. Frontends:
http://127.0.0.1:8080 and http://127.0.0.1:8081. See
[deployment](../docs/deployment.md) for production boundaries.
