# Repository contract

This file applies to every ticket and all code in this repository. Read it before
planning or making changes. Preserve this contract in any module-level guidance.

## Package root

- Use `com.parvez` as the package root for **all Java code**, including production
  code, tests, examples, and generated code. Subpackages must start with
  `com.parvez.`. Never use `com.example`.
- Keep Java source paths, package declarations, component scanning, and code
  generation settings consistent with this root.

## Required stack and dependency policy

Use the following stack:

- Java 25 or newer.
- Spring Boot 4 or newer and Spring Framework 7 or newer.
- Spring Security and Spring Authorization Server.
- Spring Data JPA and Hibernate.
- PostgreSQL and Flyway.
- Docker and Docker Compose.
- OpenAPI with springdoc.
- Spring Boot Actuator and Micrometer.
- JUnit 6, Mockito, and Testcontainers.
- Maven for building and testing Java modules.

Verify current compatible versions before adding dependencies — do not copy older
Spring Boot 2/3 tutorial config; if Spring Boot 4/Spring Framework 7 APIs differ
from older docs, use the current API and note the change.

Consult official release notes, documentation, and compatibility matrices for the
selected versions. These major versions are minimums, not permission to combine
incompatible releases. Prefer Spring Boot dependency management for supported
dependencies; verify explicit overrides and independently versioned libraries
(including springdoc). Pin reproducible versions in build configuration. Verify
the resolved JUnit 6 Jupiter/Platform versions and use Spring Boot dependency
management to keep them aligned. Never silently downgrade or replace a required stack item
to resolve a compatibility issue; report the conflict and obtain a decision.
Record version choices, compatibility evidence, and API migration notes in
`docs/architecture.md` when introducing or changing the stack.

## Important working rules — non-negotiable

- Implement core functionality completely. No TODOs, placeholder implementations,
  or stubs in core functionality.
- Never expose JPA entities through API requests or responses. Use dedicated DTOs
  and explicit mapping at the API boundary.
- Never rely on frontend-only authorization. The backend must enforce all access
  control, including role, ownership, and tenant boundaries where applicable.
- No unbounded queries. Bound result sets with pagination, limits, or bounded
  batches, including background processing and exports. Validate and cap
  client-supplied page sizes.
- Never use `ddl-auto=update` in production configuration. Use Flyway for **all**
  schema changes; configure production Hibernate schema handling as `validate`
  or `none`. Do not modify already-applied migrations; add a new migration.
- Run relevant tests and required build checks before declaring a ticket done.
  Never claim tests passed unless they were run successfully. If execution is
  blocked, report the command, blocker, and outstanding validation; do not declare
  the ticket complete.

## Build and test commands

The repository has three independent, parent-less Maven projects under
`enterprise-platform/`; there is no root POM or reactor. They are currently empty
skeletons with no application sources or tests. When adding or changing a module,
update this section and `docs/development.md` with its exact commands,
prerequisites, profiles, and service setup.

Use the Maven wrapper (`./mvnw`) when present; otherwise use `mvn`. The commands
below use `mvn`; substitute the wrapper as appropriate.

| Scope | Command | Purpose |
| --- | --- | --- |
| Auth server, from repository root | `mvn -f enterprise-platform/auth-server/pom.xml clean verify` | Verify the auth server project. |
| Task management, from repository root | `mvn -f enterprise-platform/task-management/pom.xml clean verify` | Verify the task management project. |
| KPI service, from repository root | `mvn -f enterprise-platform/kpi-service/pom.xml clean verify` | Verify the KPI service project. |
| Any backend, from its directory | `mvn clean verify` | Verify that independent project. |
| Fast unit-test feedback, from the applicable Maven project | `mvn test` | Run unit tests during development; this does not replace final verification. |

Configure Maven Surefire/Failsafe as needed so `verify` actually runs the required
JUnit 6, Mockito, and Testcontainers tests. Docker must be available for
Testcontainers. Do not skip tests to obtain a successful final build. For shared
build, dependency, or cross-module changes, verify all three independent projects.
For documentation-only tickets, inspect the diff and run `git diff --check`.
For skeleton builds, explicitly report that no application tests exist yet.

## Documentation and architectural decisions

Create these files when first needed and keep them current in the same ticket as
the corresponding change:

- `docs/architecture.md`: module boundaries, package structure, stack versions,
  compatibility sources, and current architectural decisions.
- `docs/decisions/NNNN-short-title.md`: an architectural decision record for each
  significant decision, with context, options, decision, consequences, and any
  compatibility or API changes. Link each record from `docs/architecture.md`.
- `docs/development.md`: exact commands per module, local setup, Docker Compose,
  test prerequisites, and required environment variables without real secrets.
- `docs/api.md`: API contracts, DTO changes, pagination, and error behavior.
- `docs/security.md`: authentication, authorization, and access-control decisions.
- `docs/database.md`: schema decisions and Flyway migration/operational guidance.

Update `README.md` when setup or entry points change, and link to relevant docs.
At ticket completion, report what changed, the documentation updated, the commands
run and their results, and any unresolved blockers.
