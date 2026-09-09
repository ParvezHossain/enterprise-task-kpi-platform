# Enterprise Platform

TICKET-0001 repository skeleton. The three backend projects are independent,
parent-less Maven JAR projects with no dependencies, Java sources, or tests yet.
The frontend pages and Compose file are placeholders; no application runs yet.

## Structure

```text
enterprise-platform/
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── auth-server/
│   └── pom.xml
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

Empty JAR warnings and reports of no sources/tests are expected at this stage.
There is no root reactor POM. Exact compatible Spring versions and application
bootstraps belong to subsequent tickets; follow [AGENTS.md](../AGENTS.md).

The Compose placeholder defines no services and cannot start the platform.
The `.env.example` template contains no active variables yet. Open either frontend
`index.html` directly in a browser to view its static placeholder.

See [development](../docs/development.md), [architecture](../docs/architecture.md),
and the [backlog](../docs/tickets/backlog.md) for commands and subsequent work.
