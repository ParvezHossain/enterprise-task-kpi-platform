# Enterprise Platform

The three backend projects are independent, parent-less Maven projects.
[Auth Server](auth-server/README.md) is a Boot application with PostgreSQL
integration tests. Task, KPI, frontend pages, and Compose remain skeletons.

## Structure

```text
enterprise-platform/
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── auth-server/
│   ├── pom.xml
│   ├── README.md
│   └── src/
│       ├── main/
│       │   ├── java/com/parvez/auth/
│       │   │   ├── AuthServerApplication.java
│       │   │   └── config/SecurityConfiguration.java
│       │   └── resources/application.yml
│       └── test/java/com/parvez/auth/AuthServerApplicationIT.java
├── task-management/
│   └── pom.xml
├── kpi-service/
│   └── pom.xml
├── frontend/
│   ├── task-management-ui/
│   │   └── index.html
│   └── kpi-ui/
│       └── index.html
├── requests/
│   └── README.md
└── docs/
    └── README.md
```

## Verify

Use Java 25+ and Maven. From this directory, run each independent build:

```sh
mvn -f auth-server/pom.xml clean verify
mvn -f task-management/pom.xml clean verify
mvn -f kpi-service/pom.xml clean verify
```

Docker is required for auth-server integration tests. Empty JAR warnings and
reports of no sources/tests remain expected for Task and KPI. There is no root
reactor POM; follow [AGENTS.md](../AGENTS.md).

The Compose placeholder defines no services and cannot start the platform.
The `.env.example` template documents auth-server connection variables. Open either frontend
`index.html` directly in a browser to view its static placeholder.

See [development](../docs/development.md), [architecture](../docs/architecture.md),
and the [backlog](../docs/tickets/backlog.md) for commands and subsequent work.
