````
# Enterprise Platform — Implementation Backlog

## Table of Contents

- [Phase 0 — Analysis & Repository Setup](#phase-0--analysis--repository-setup)
  - [TICKET-0000 — Create AGENTS.md](#ticket-0000--create-agentsmd-stack-contract)
  - [TICKET-0001 — Inspect & Scaffold Repository Skeleton](#ticket-0001--inspect--scaffold-repository-skeleton)
  - [TICKET-0002 — Decide & Document Core Architectural Choices](#ticket-0002--decide--document-core-architectural-choices)
- [Phase 1 — Auth Server](#phase-1--auth-server)
- [Phase 2 — Task Management Service](#phase-2--task-management-service)
- [Phase 3 — KPI Service](#phase-3--kpi-service)
- [Phase 4 — Frontend](#phase-4--frontend)
- [Phase 5 — Docker & Environment](#phase-5--docker--environment)
- [Phase 6 — Cross-Cutting Hardening & Documentation](#phase-6--cross-cutting-hardening--documentation)

---

# Phase 0 — Analysis & Repository Setup

## TICKET-0000 — Create AGENTS.md (Stack Contract)

### Goal

Give Codex a persistent, repository-level contract so every future session and ticket enforces the same stack, package root, and non-negotiable engineering rules without requiring them to be re-entered.

### Tasks

Create `AGENTS.md` at the repository root containing the following requirements.

#### Package Root

- All Java code must use the package root:
  - `com.parvez`
- Never use:
  - `com.example`

#### Required Technology Stack

- Java 25+
- Spring Boot 4+
- Spring Framework 7+
- Spring Security
- Spring Authorization Server
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Docker / Docker Compose
- OpenAPI / springdoc
- Spring Boot Actuator
- Micrometer
- JUnit 6
- Mockito
- Testcontainers

#### Version Compatibility Rule

> Verify current compatible versions before adding dependencies.

Do not copy configuration from older Spring Boot 2.x/3.x tutorials.

If Spring Boot 4 or Spring Framework 7 APIs differ from older documentation:

1. Use the current API.
2. Do not force compatibility with outdated examples.
3. Note the relevant API/configuration change where appropriate.

#### Non-Negotiable Working Rules

The repository must enforce the following rules:

- No `TODO` placeholders for core functionality.
- Never expose JPA entities directly through REST APIs.
- Frontend authorization is UX-only.
- Backend must enforce all access control.
- Never introduce unbounded queries.
- Never use `ddl-auto=update` in production configuration.
- All schema changes must be performed through Flyway.
- Run tests before declaring a ticket complete.
- Security-critical functionality must have meaningful test coverage.
- Architectural decisions must be documented.

#### Build & Test Commands

Document build and test commands for each module, including commands such as:

```bash
mvn clean verify
````

Also document module-specific commands where applicable.

#### Documentation Rules

When a ticket makes an architectural decision, update the appropriate file under:

```
docs/
```

At minimum, architectural, security, database, API, and deployment decisions should be reflected in the relevant documentation.

### Acceptance Criteria

- [ ] `AGENTS.md` exists at the repository root.
- [ ] A fresh Codex session with no additional context follows the repository contract.
- [ ] New Java code uses `com.parvez`.
- [ ] The specified technology stack is followed.
- [ ] Outdated Spring Boot 2/3 configuration is not copied into the project.

---

## TICKET-0001 — Inspect & Scaffold Repository Skeleton

### Goal

Create the top-level repository structure with empty-but-valid modules.

### Repository Structure

Create:

```
enterprise-platform/
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
├── requests/
├── docs/
│
├── auth-server/
├── task-management/
├── kpi-service/
│
└── frontend/
    ├── task-management-ui/
    │   └── index.html
    └── kpi-ui/
        └── index.html
```

### Backend Modules

Create independently buildable Maven projects:

- `auth-server/`
- `task-management/`
- `kpi-service/`

Each project must:

- Have a valid `pom.xml`.
- Be independently buildable.
- Use the correct `groupId`.
- Use the correct `artifactId`.

Do not introduce a shared Maven parent POM unless there is a deliberate architectural reason.

If a shared parent POM is introduced:

- Document the decision.
- Document why it is preferable to independent projects.

### Root Files

Create:

- `README.md`
- `.gitignore`
- `.env.example`
- `docker-compose.yml` as a placeholder
- `requests/`
- `docs/`

### `.gitignore`

The root `.gitignore` must cover at least:

```
target/
.env
.idea/
.vscode/
*.iml
node_modules/
```

Include other common Java/IDE-generated files where appropriate.

### Acceptance Criteria

- [ ] Repository structure matches the README.
- [ ] All three backend modules contain valid `pom.xml` files.
- [ ] Each backend module is independently buildable.
- [ ] `groupId` and `artifactId` are correct.
- [ ] Frontend applications contain placeholder `index.html` files.
- [ ] Root `.gitignore` covers Maven, environment, IDE, and frontend artifacts.

---

## TICKET-0002 — Decide & Document Core Architectural Choices

### Goal

Lock in architectural decisions that the rest of the backlog depends on before implementing service functionality.

### Documentation

Create:

```
docs/architecture.md
```

The document must contain a decision table with the following columns:

| Decision | Chosen | Alternatives Considered | Why |
| --- | --- | --- | --- |

### Required Decisions

#### 1\. Framework Versions

Explicitly select and document compatible versions for:

- Spring Boot
- Spring Framework
- Spring Security
- Spring Authorization Server

Verify current compatibility before selecting versions.

#### 2\. PostgreSQL Architecture

Choose between:

- One PostgreSQL server with three databases.
- Three PostgreSQL containers.

Document:

- Local development impact.
- Operational complexity.
- Isolation requirements.
- Why the selected approach is preferred.

#### 3\. KPI ↔ Task Integration

Choose between:

- REST pull.
- Event-driven read model.

Prefer the simplest approach that satisfies the requirements and document the reasoning.

#### 4\. Idempotency

Define the idempotency strategy for:

- Approve.
- Assign.
- Close.

Document:

- Key format.
- Storage location.
- Persistence behavior.
- Replay behavior.
- Concurrency behavior.

#### 5\. KPI Caching

Choose either:

- Implement caching.
- No caching initially.

If caching is deferred, document how caching can be introduced later.

### Acceptance Criteria

- [ ] `docs/architecture.md` exists.
- [ ] The document contains a decisions table.
- [ ] All required architectural decisions are explicitly documented.
- [ ] Alternatives and rationale are recorded.
- [ ] Decisions are referenced by subsequent tickets where applicable.

---

# Phase 1 — Auth Server

## TICKET-0101 — Auth Server Project Bootstrap

### Tasks

Set up the `auth-server` Spring Boot application with:

- Spring Security
- Spring Authorization Server
- Spring Data JPA
- PostgreSQL driver
- Flyway
- Actuator
- Validation
- springdoc / OpenAPI

### Configuration

Configure:

```
application.yml
```

for:

- Local development.
- Docker execution.

### Package

Use:

```
com.parvez.auth
```

### Acceptance Criteria

- [ ] Application boots successfully.
- [ ] Application starts with an empty context.
- [ ] `/actuator/health` returns HTTP `200`.

---

## TICKET-0102 — Auth Database Schema (Flyway V1–V3)

### Tasks

Create Flyway migrations for:

- `users`
- `roles`
- `permissions`
- `user_roles`
- `role_permissions`

Also create the Spring Authorization Server tables:

- `oauth2_registered_client`
- `oauth2_authorization`
- `oauth2_authorization_consent`

### Database Constraints

Add:

- Index on `users.email`.
- Foreign-key constraints.
- Required unique constraints.

Configure Hibernate to use:

```
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

### Acceptance Criteria

- [ ] Flyway migrations execute successfully against a fresh database.
- [ ] Hibernate validates the schema successfully.
- [ ] No schema mismatch exists between entities and database tables.

---

## TICKET-0103 — User, Role, Permission Domain & Repositories

### Tasks

Implement JPA entities for:

- User
- Role
- Permission

Implement Spring Data repositories for each.

### Seed Data

Seed the following roles:

- `ADMIN`
- `PROJECT_MANAGER`
- `TEAM_LEADER`
- `EMPLOYEE`

Seed the complete permission list defined by the master specification.

Seed data must be:

- Clearly labeled as development/test data.
- Separate from production schema migrations where appropriate.
- Implemented through a Flyway data migration.

### Testing

Create a Testcontainers PostgreSQL integration test that confirms:

- Roles are seeded.
- Permissions are seeded.
- Roles are queryable.
- Permissions are queryable.

### Acceptance Criteria

- [ ] Entities exist.
- [ ] Repositories exist.
- [ ] Required roles are seeded.
- [ ] Required permissions are seeded.
- [ ] Testcontainers integration test passes.

---

## TICKET-0104 — Password Hashing & User Registration / Service Layer

### Tasks

Implement `UserService`.

Use:

- Argon2id, or
- The current Spring Security default if Argon2id cannot be integrated cleanly.

If the default is used instead of Argon2id:

- Document the decision.
- Explain the compatibility reason.

### Security Requirements

- Never log raw passwords.
- Never log password hashes unnecessarily.
- Never expose passwords through API responses.
- Never include passwords in exceptions.

### Acceptance Criteria

- [ ] Password hashes differ between calls due to salting.
- [ ] `verify()` successfully validates the original password.
- [ ] Incorrect passwords fail verification.
- [ ] Registration flow produces no password/token leakage in logs.
- [ ] No raw password logging exists anywhere in the implementation.

---

## TICKET-0105 — Spring Authorization Server Configuration (OIDC + JWT)

### Tasks

Implement `AuthorizationServerConfig`.

Configure:

- Authorization Code flow.
- PKCE.
- Refresh tokens.
- OIDC discovery.
- JWT access tokens.
- RSA signing keys.

RSA keys must be:

- Generated or loaded through configuration.
- Never hardcoded into source code.

### OAuth Clients

Register at least:

- `task-management-ui`
- `kpi-ui`

Clients may be registered using:

- Flyway seed data, or
- `CommandLineRunner` guarded to development/test profiles.

### JWT Claims

JWTs must include:

- Roles.
- Authorities.

### Testing

Add:

```
requests/auth.http
```

containing a manual flow for the complete authorization-code + PKCE process.

### Acceptance Criteria

- [ ] Authorization Code + PKCE flow works.
- [ ] Refresh token flow works.
- [ ] OIDC discovery works.
- [ ] JWT is signed using RSA.
- [ ] JWT contains roles/authorities.
- [ ] Manual `.http` flow returns a valid JWT.

---

## TICKET-0106 — Login / Logout & Consent Handling

### Tasks

Implement:

- Login form/endpoint.
- Logout.
- Consent handling.

For first-party trusted clients, consent may be automatically approved if appropriate.

If auto-approval is selected:

- Document why it is safe.
- Document which clients are trusted.
- Document the security implications.

### Acceptance Criteria

- [ ] Manual login succeeds.
- [ ] Token issuance succeeds.
- [ ] Logout succeeds.
- [ ] Session/token state is correctly cleared.
- [ ] Consent behavior is documented.

---

## TICKET-0107 — Exception Handling, Validation & Problem Details

### Tasks

Implement:

```
@RestControllerAdvice
```

Return RFC 9457 Problem Details for:

- Validation errors.
- Bad credentials.
- Malformed requests.
- Unexpected exceptions.

### Security Requirements

- Never expose stack traces.
- Never expose internal implementation details.
- Never leak sensitive authentication information.

### Acceptance Criteria

- [ ] Invalid login returns Problem Details.
- [ ] Malformed requests return Problem Details.
- [ ] Validation errors return Problem Details.
- [ ] Stack traces are not leaked.

---

## TICKET-0108 — Auth Server Observability

### Tasks

Configure:

- Actuator.
- Micrometer.
- Health.
- Info.
- Metrics.
- Prometheus.
- Structured JSON logging.
- `traceId`.
- `requestId`.

### Security

Protect sensitive Actuator endpoints.

Public:

- `/actuator/health`
- `/actuator/info` where appropriate.

Protected:

- Metrics.
- Prometheus.
- Other sensitive endpoints.

### Acceptance Criteria

- [ ] `/actuator/prometheus` exposes metrics when authenticated.
- [ ] Sensitive endpoints reject unauthenticated requests.
- [ ] Logs contain correlation information.
- [ ] Logs are structured JSON.

---

## TICKET-0109 — Auth Server Test Suite

### Unit Tests

Cover:

- Password hashing.
- Password verification.
- Token issuance logic.

### Integration Tests

Use Testcontainers PostgreSQL to test:

- Full OIDC flow.
- Invalid client.
- Expired token.
- Revoked refresh token.

### Acceptance Criteria

- [ ] `mvn clean verify` passes.
- [ ] Security-critical paths have meaningful test coverage.
- [ ] Tests validate behavior, not merely line coverage.

---

## TICKET-0110 — Auth Server Dockerfile & Service README

### Tasks

Create a multi-stage Dockerfile:

1. Maven build stage.
2. Minimal JRE runtime stage.

Runtime container must:

- Run as a non-root user.
- Expose the required application port.
- Include health-check support.

Create:

```
auth-server/README.md
```

Document:

- Standalone execution.
- Configuration.
- Environment variables.
- Testing.
- Docker execution.

### Acceptance Criteria

- [ ] `docker build` succeeds.
- [ ] Container starts.
- [ ] Health check passes.

---

# Phase 2 — Task Management Service

## TICKET-0201 — Task Management Project Bootstrap

### Tasks

Set up Spring Boot application with:

- Spring Security.
- OAuth2 Resource Server.
- Spring Data JPA.
- PostgreSQL.
- Flyway.
- Actuator.
- Validation.
- springdoc / OpenAPI.

Configure JWT validation using the Auth Server:

- Issuer.
- Signature.
- Expiry.
- Audience.

### Package

Use:

```
com.parvez.task
```

### Acceptance Criteria

- [ ] Application boots.
- [ ] Protected endpoint returns `401` without authentication.
- [ ] Valid Auth Server JWT reaches the controller.

---

## TICKET-0202 — Task Database Schema

### Tasks

Create Flyway migrations for:

- `tasks`
- `teams`
- `projects`
- `task_audit_log`

Add optimistic-locking `version` column.

Add foreign keys and indexes for:

- `assigned_to`
- `team_id`
- `status`
- `created_at`
- `due_date`
- `project_id`

### Documentation

Document index rationale in:

```
docs/database.md
```

### Acceptance Criteria

- [ ] Flyway migration succeeds.
- [ ] Hibernate schema validation succeeds.
- [ ] Required indexes exist.
- [ ] Required foreign keys exist.

---

## TICKET-0203 — Task Domain Model & State Machine

### Tasks

Implement:

- Task aggregate.
- `TaskStatus` enum.
- Explicit state-machine component.

Use an appropriate:

- Strategy pattern, or
- State pattern.

The state machine must live outside controllers, within the application/domain layer.

### State Machine Requirements

The state machine must:

- Encode every valid transition.
- Reject every invalid transition.
- Be independently unit-testable.

Example invalid transition:

```
DRAFT → CLOSED
```

must fail.

### Acceptance Criteria

- [ ] Every valid transition has a unit test.
- [ ] Every invalid transition is rejected.
- [ ] State transition logic is not implemented in controllers.

---

## TICKET-0204 — Authorization Policy Layer

### Tasks

Implement a dedicated authorization/policy component.

Do not scatter authorization logic across controllers using independent:

```
@PreAuthorize
```

expressions.

Method security annotations should delegate to the policy layer.

### Authorization Matrix

Implement the authorization matrix defined in README section 20.

### Acceptance Criteria

- [ ] Dedicated policy component exists.
- [ ] Every role/action combination is tested.
- [ ] Tests cover every allow/deny cell in the authorization matrix.
- [ ] Controllers do not contain duplicated authorization logic.

---

## TICKET-0205 — Task Creation, DTOs & Validation

### Endpoint

```
POST /api/v1/tasks
```

Authorized roles:

- Admin.
- Project Manager.

### Requirements

Implement request/response DTOs.

Never expose JPA entities.

Validate:

- Title.
- Description.
- Priority.
- Project.
- Team.
- Due date.

Use Jakarta Bean Validation.

### Acceptance Criteria

- [ ] PM can create a valid task → `201`.
- [ ] Employee receives `403`.
- [ ] Invalid payload returns `400`.
- [ ] Invalid payload uses Problem Details.
- [ ] JPA entities are never exposed.

---

## TICKET-0206 — Task Approval & Assignment

### Endpoints

```
POST /api/v1/tasks/{id}/approve
POST /api/v1/tasks/{id}/assign
```

### Approval

Approval must be:

- Team Leader accessible.
- Policy gated.
- State-machine validated.

### Assignment

Assignment must validate:

- Employee exists.
- Employee belongs to the team.
- Team Leader manages the team.
- Task is in the correct state.

Use optimistic locking.

On version conflict:

```
409 Conflict
```

### Acceptance Criteria

- [ ] Approval obeys authorization policy.
- [ ] Assignment validates team membership.
- [ ] Assignment validates Team Leader ownership.
- [ ] Invalid state transitions are rejected.
- [ ] Optimistic locking is enabled.
- [ ] Concurrent assignment produces exactly one success.
- [ ] Losing request receives `409`.

---

## TICKET-0207 — Status Update, Completion & Close

### Endpoints

```
PATCH /api/v1/tasks/{id}/status
POST /api/v1/tasks/{id}/close
```

### Status Update

Employees may move their own task:

```
IN_PROGRESS → COMPLETED
```

### Close

Only:

- PM.
- Admin.

may close:

```
COMPLETED → CLOSED
```

### IDOR Protection

Prevent Employee A from modifying Employee B's task by guessing its ID.

Choose and document one disclosure policy:

- `403 Forbidden`, or
- `404 Not Found`.

Document the security rationale.

### Acceptance Criteria

- [ ] Employees can update only their own tasks.
- [ ] Employees cannot update another employee's task.
- [ ] PM/Admin can close completed tasks.
- [ ] Invalid transitions fail.
- [ ] IDOR test passes.

---

## TICKET-0208 — Task Query Endpoints, Filtering & Pagination

### Endpoints

Implement:

```
GET /api/v1/tasks
GET /api/v1/tasks/me
GET /api/v1/tasks/team
GET /api/v1/tasks/{id}
GET /api/v1/tasks/{id}/history
```

### Pagination

Pagination must include:

- Configurable maximum page size.
- Server-side page-size enforcement.
- Sorting.

### Filtering

Use Spring Data Specifications for:

- Status.
- Priority.
- Assigned user.
- Team.
- Project.
- Created by.
- Date range.

### Security

Visibility must be role-scoped.

Employees must never see other employees' tasks through an unauthorized endpoint variant.

### Acceptance Criteria

- [ ] Pagination works.
- [ ] Page size is capped server-side.
- [ ] Sorting works.
- [ ] All specified filters work.
- [ ] Employee visibility is restricted.
- [ ] Integration tests verify role-scoped access.
- [ ] Unbounded queries are impossible.

---

## TICKET-0209 — Idempotency for Approve / Assign / Close

### Tasks

Implement the idempotency mechanism selected in TICKET-0002.

Example:

```
Idempotency-Key
```

Store idempotency information in a database-backed mechanism such as:

- Dedicated table.
- Audit-row association.

The mechanism must survive multiple application instances.

### Behavior

For the same idempotency key:

1. First request executes normally.
2. Second request returns the original result.
3. Transition is not executed twice.

### Acceptance Criteria

- [ ] Idempotency keys are persisted.
- [ ] Duplicate requests do not execute twice.
- [ ] Original response is replayed.
- [ ] Behavior survives application restarts/instances.

---

## TICKET-0210 — Immutable Audit Trail

### Tasks

Implement:

- `TaskAuditEvent` entity.
- Repository.

Use a domain-event/observer pattern to create audit records for:

- Created.
- Approved.
- Rejected.
- Assigned.
- Status changed.
- Completed.
- Closed.
- Reopened.

### Immutability

Audit records must not have:

- Update endpoints.
- Delete endpoints.
- Service methods that modify existing records.

### Acceptance Criteria

- [ ] Every transition creates an audit event.
- [ ] Audit events are queryable.
- [ ] Audit events cannot be edited.
- [ ] Audit events cannot be deleted through application services.

---

## TICKET-0211 — Exception Handling, Problem Details, CORS & CSRF

### Exception Handling

Implement `@RestControllerAdvice` for:

- Validation errors.
- Authentication errors.
- Authorization errors.
- Not found.
- Business-rule violations.
- Optimistic-locking conflicts.
- Database constraint violations.

Return correct HTTP statuses and RFC 9457 Problem Details.

### CORS

Configure explicit CORS.

Allowed origins must be configurable.

Never use:

```
*
```

for production origins.

### CSRF

Document the CSRF decision in:

```
docs/security.md
```

If using a stateless JWT resource server:

- Explain why CSRF protection is unnecessary for the chosen authentication mechanism.
- Document any browser/token-storage assumptions.

### Acceptance Criteria

- [ ] Every major error branch has a test.
- [ ] Correct status codes are returned.
- [ ] Problem Details shape is consistent.
- [ ] CORS origins are configurable.
- [ ] Wildcard CORS is not used in production.
- [ ] CSRF decision is documented.

---

## TICKET-0212 — Observability & Business Metrics

### Tasks

Configure:

- Actuator.
- Micrometer.
- Prometheus.

Create counters for:

```
tasks.created
tasks.approved
tasks.assigned
tasks.completed
tasks.closed
tasks.overdue
```

Also include:

- Request-duration timers.
- Correlation IDs.
- Structured logging.

### Acceptance Criteria

- [ ] Custom counters are emitted.
- [ ] Request duration is measured.
- [ ] Correlation IDs appear in logs.
- [ ] Prometheus endpoint exposes the metrics.
- [ ] Metrics increase after exercising the API.

---

## TICKET-0213 — Task Management Test Suite

### Test Categories

Implement tests covering:

- State machine.
- Authorization policy.
- Repository behavior.
- Testcontainers integration.
- IDOR protection.
- Concurrency.
- REST API behavior.
- Problem Details.
- Pagination/filtering.

### Concurrency

Concurrency tests must actually execute operations in parallel.

Recommended tools:

```
ExecutorService
CountDownLatch
```

Do not simulate concurrency with sequential calls.

### Acceptance Criteria

- [ ] `mvn clean verify` passes.
- [ ] State machine is fully tested.
- [ ] Authorization matrix is fully tested.
- [ ] IDOR tests pass.
- [ ] Integration tests use Testcontainers.
- [ ] Concurrency tests execute in parallel.
- [ ] Exactly-one-winner semantics are asserted.

---

## TICKET-0214 — Task Management Dockerfile & Service README

### Tasks

Create:

```
task-management/Dockerfile
task-management/README.md
```

Dockerfile requirements:

- Multi-stage build.
- Maven build stage.
- Minimal JRE runtime.
- Non-root runtime user.
- Health check support.

README must document:

- Local execution.
- Configuration.
- Database setup.
- Testing.
- Docker execution.
- API access.

---

# Phase 3 — KPI Service

## TICKET-0301 — KPI Service Project Bootstrap

### Tasks

Set up Spring Boot application with:

- OAuth2 JWT Resource Server.
- Auth Server issuer validation.
- Own PostgreSQL database.
- Actuator.
- springdoc / OpenAPI.

### Package

Use:

```
com.parvez.kpi
```

### Acceptance Criteria

- [ ] Application boots.
- [ ] JWT validation is configured.
- [ ] KPI service uses its own database.
- [ ] Actuator health works.

---

## TICKET-0302 — KPI ↔ Task Integration

### Goal

Implement the integration architecture selected in TICKET-0002.

Possible approaches:

### Option A — REST Adapter

KPI Service calls Task Management read endpoints through a dedicated adapter such as:

```
TaskClient
```

### Option B — Scheduled Read Model

KPI Service periodically synchronizes the required Task Management data into its own read model.

### Rules

- KPI Service must never access the Task Management database directly.
- Integration failures must degrade gracefully.
- External integration code must be isolated behind an adapter.

### Acceptance Criteria

- [ ] Selected integration approach matches `docs/architecture.md`.
- [ ] KPI Service does not access Task Management DB directly.
- [ ] Integration test verifies read-model construction.
- [ ] Task Management unavailability is handled gracefully.

---

## TICKET-0303 — KPI Aggregate Queries

### Goal

Perform KPI calculations at the database level.

### Required Aggregates

Calculate:

- Total assigned tasks.
- Total completed tasks.
- Total closed tasks.
- Total overdue tasks.
- Completion percentage.
- On-time percentage.
- Average completion time.

Use:

- `COUNT`
- `SUM`
- `AVG`
- `GROUP BY`
- Native or derived database queries as appropriate.

Do not load large entity collections into Java and calculate aggregates using loops.

### Acceptance Criteria

- [ ] Aggregates execute in the database.
- [ ] Seeded test dataset produces expected values.
- [ ] No N+1 queries occur.
- [ ] Query-count assertion is included where useful.
- [ ] Hibernate statistics or equivalent verifies query behavior.

---

## TICKET-0304 — Deterministic Scoring & Ranking Model

### Tasks

Implement the documented scoring formula using:

- Completion rate.
- On-time rate.
- Completed count.
- Overdue penalty.
- Priority weighting.

The scoring function must be:

- Pure.
- Deterministic.
- Unit-testable.

### Documentation

Document the formula in:

```
docs/api.md
```

### Acceptance Criteria

- [ ] Formula is documented.
- [ ] Function is deterministic.
- [ ] Fixed inputs produce exact expected scores.
- [ ] Rankings are deterministic.
- [ ] No randomness exists.

---

## TICKET-0305 — KPI REST Endpoints & Authorization

### Endpoints

Implement:

```
GET /api/v1/kpis/me
GET /api/v1/kpis/team
GET /api/v1/kpis/company
GET /api/v1/kpis/ranking
GET /api/v1/kpis/top-performers
GET /api/v1/kpis/trends
```

### Authorization

Scope each endpoint according to the authorization policy.

Employees must not access sensitive company-wide data unless explicitly authorized.

### Acceptance Criteria

- [ ] All KPI endpoints are implemented.
- [ ] Authorization is role-scoped.
- [ ] Employees cannot access unauthorized company-wide data.
- [ ] List-shaped responses support pagination.
- [ ] Integration tests cover each role.

---

## TICKET-0306 — KPI Caching or Documented Deferral

### Option A — Implement Caching

If caching is selected:

Document:

- Cache key.
- TTL.
- Invalidation strategy.
- Refresh behavior.
- Failure behavior.

### Option B — Defer Caching

If caching is deferred, add a clear note to:

```
docs/architecture.md
```

Document:

- Why caching is not currently required.
- Which queries are candidates.
- How caching can be introduced later.
- What invalidation strategy should be considered.

### Acceptance Criteria

- [ ] Decision matches TICKET-0002.
- [ ] Caching behavior is explicitly documented or implemented.

---

## TICKET-0307 — KPI Observability, Test Suite & Dockerfile

### Tasks

Implement:

- Actuator.
- Micrometer.
- Business metrics.
- Integration tests.
- Unit tests.
- Dockerfile.
- Service README.

### Acceptance Criteria

- [ ] Test suite passes.
- [ ] Observability is available.
- [ ] Docker image builds successfully.
- [ ] Container runs as non-root.
- [ ] Service health check passes.

---

# Phase 4 — Frontend

## TICKET-0401 — Shared Design System

### Goal

Create a common Tailwind-based design system used consistently by both frontend applications.

### Shared Components

Implement:

- Sidebar.
- Top navigation.
- Cards.
- Tables.
- Badges.
- Modals.
- Toasts.
- Loading states.
- Empty states.
- Error states.

### Shared Assets

Place common CSS/JS in a location that both applications can reference.

### Acceptance Criteria

- [ ] Both frontends use the shared design system.
- [ ] Components have consistent styling.
- [ ] Static style-guide page renders every shared component.

---

## TICKET-0402 — Browser OAuth2/OIDC Integration

### Goal

Implement the OAuth2/OIDC login architecture selected in TICKET-0002.

Possible approach:

- PKCE public client.
- BFF.

Document the selected approach.

### Security Requirements

- No client secret in JavaScript.
- Carefully document token storage.
- Avoid storing raw long-lived tokens in `localStorage`.
- Justify the selected session/token-storage mechanism.

### Acceptance Criteria

- [ ] Login works.
- [ ] Protected page loads after authentication.
- [ ] API requests use the appropriate access token/session.
- [ ] Logout clears authentication state.
- [ ] No client secret exists in browser code.

---

## TICKET-0403 — Task Management UI

### Pages

Implement:

- Login.
- Dashboard.
- My Tasks.
- Team Tasks.
- All Tasks.
- Create Task.
- Task Details.
- Approval.
- Assignment.
- History.
- Profile.

### Authorization

Role-gate:

- Navigation.
- Pages.
- Actions.

Frontend authorization is UX-only.

Backend authorization remains authoritative.

### Acceptance Criteria

Perform a manual walkthrough as:

- Admin.
- Project Manager.
- Team Leader.
- Employee.

Verify that each role sees only the appropriate pages and actions.

---

## TICKET-0404 — KPI UI / Dashboards

### Pages

Implement:

- KPI Dashboard.
- My Performance.
- Team Performance.
- Company Performance.
- Top Performers.
- Trend Analysis.

### Charts

Include lightweight visualizations for:

- Completion trend.
- Completed vs overdue.
- Ranking.
- Status distribution.

### Acceptance Criteria

- [ ] Seeded data appears correctly.
- [ ] KPI values match API responses.
- [ ] Role-based visibility works.
- [ ] Charts render correctly.
- [ ] Manual walkthrough succeeds.

---

# Phase 5 — Docker & Environment

## TICKET-0501 — Docker Compose Databases

### Tasks

Configure PostgreSQL service(s) according to TICKET-0002.

Include:

- Health checks.
- Named volumes.
- `.env`-driven credentials.
- Persistent storage.

### Acceptance Criteria

- [ ] Database architecture matches `docs/architecture.md`.
- [ ] Health checks work.
- [ ] Data persists across container recreation.
- [ ] Credentials are not hardcoded.

---

## TICKET-0502 — Docker Compose Application Services

### Services

Wire:

- `auth-server`
- `task-management`
- `kpi-service`

### Requirements

Use:

```
depends_on:
  condition: service_healthy
```

where supported by the chosen Compose configuration.

Configure:

- Correct networking.
- Environment variables.
- `.env.example`.
- Service URLs.
- Database connections.
- Auth Server issuer.

### Acceptance Criteria

Run:

```
docker compose up --build
```

Verify:

- [ ] All services start.
- [ ] Auth Server health is `UP`.
- [ ] Task Management health is `UP`.
- [ ] KPI Service health is `UP`.

---

## TICKET-0503 — Docker Compose Frontends & Reverse Proxy

### Tasks

Serve both frontend applications:

- Static hosting, or
- Nginx/reverse proxy.

If a reverse proxy is selected, document the decision.

Configure end-to-end:

- CORS.
- Frontend origins.
- Auth Server URLs.
- API URLs.
- Routing.

### Acceptance Criteria

Complete the entire workflow through Docker Compose:

```
Login
  ↓
Create task
  ↓
Approve
  ↓
Assign
  ↓
Complete
  ↓
Close
  ↓
KPI dashboard
```

---

# Phase 6 — Cross-Cutting Hardening & Documentation

## TICKET-0601 — Security Headers & Auth Rate Limiting

### Security Headers

Configure:

- Content Security Policy (CSP).
- `X-Content-Type-Options`.
- `Referrer-Policy`.
- HSTS when TLS is enabled.

### Rate Limiting

Add basic rate limiting to the Auth Server login endpoint.

Document whether rate limiting is:

- In-memory, or
- Reverse-proxy based.

If in-memory:

- Explicitly document horizontal-scaling limitations.

Prefer reverse-proxy infrastructure for scalable rate limiting where appropriate.

### Acceptance Criteria

- [ ] Security headers are configured.
- [ ] Login endpoint has rate limiting.
- [ ] Scaling limitations are documented.
- [ ] HSTS is enabled only under appropriate TLS conditions.

---

## TICKET-0602 — Request / Response Correlation Across Services

### Goal

Allow one user action to be traced across the entire platform.

### Flow

```
Browser
  ↓
Task / KPI Service
  ↓
Auth / downstream services where applicable
  ↓
Logs
```

Propagate:

- Trace ID.
- Request/correlation ID.

### Acceptance Criteria

For a request that touches multiple services:

- [ ] Same correlation ID appears across service logs.
- [ ] Browser/request metadata is propagated.
- [ ] Logs can be used to trace the complete request.

---

## TICKET-0603 — End-to-End Workflow Test

### Goal

Automate the complete workflow defined in README section 66.

### Workflow

```
Create users
  ↓
Authenticate
  ↓
Create task
  ↓
Approve
  ↓
Assign
  ↓
Start
  ↓
Complete
  ↓
Close
  ↓
Verify KPI
```

### Implementation

Prefer an integration test spanning real services using:

- Testcontainers, or
- Docker Compose-based test harness.

### Acceptance Criteria

- [ ] Complete workflow is automated.
- [ ] Authentication is real.
- [ ] Service boundaries are exercised.
- [ ] KPI reflects the completed workflow.
- [ ] Test is reliable and non-flaky.
- [ ] Test passes in CI-like conditions.

---

## TICKET-0604 — Seed Data for Local Development

### Goal

Provide meaningful development data without contaminating production environments.

### Seed

Create one user for each role:

- Admin.
- Project Manager.
- Team Leader.
- Employee.

Also create:

- Sample teams.
- Sample projects.
- Sample tasks.
- Task history.

Seed enough data for KPI charts to be meaningful.

### Requirements

Clearly label all seed data as:

```
NON-PRODUCTION
```

### Acceptance Criteria

- [ ] Each role has a development user.
- [ ] Teams exist.
- [ ] Projects exist.
- [ ] Tasks exist.
- [ ] Historical task data exists.
- [ ] KPI dashboards have meaningful data.
- [ ] Production environments do not automatically receive development seed data.

---

## TICKET-0605 — Documentation Pass

### Finalize

```
README.md

docs/
├── architecture.md
├── security.md
├── database.md
├── api.md
└── deployment.md
```

Also finalize:

```
auth-server/README.md
task-management/README.md
kpi-service/README.md
```

### Documentation Requirements

A new developer must be able to follow the documentation and:

1. Clone the repository.
2. Configure environment variables.
3. Start dependencies.
4. Build the backend.
5. Start the services.
6. Start the frontend.
7. Authenticate.
8. Execute the complete workflow.
9. Access API documentation.
10. Run the test suite.

The developer should not need to ask for additional instructions.

### Acceptance Criteria

- [ ] Architecture documentation is complete.
- [ ] Security documentation is complete.
- [ ] Database documentation is complete.
- [ ] API documentation is complete.
- [ ] Deployment documentation is complete.
- [ ] Root README is complete.
- [ ] All service READMEs are complete.
- [ ] Full local setup works using documentation only.

---

## TICKET-0606 — Final Engineering Review

### Goal

Perform a final review against README section 84.

### Review Areas

Review and resolve all unchecked items in:

- [ ] Architecture
- [ ] Security
- [ ] Database
- [ ] REST API
- [ ] Performance
- [ ] Testing
- [ ] Frontend
- [ ] DevOps
- [ ] Documentation

### Final Requirements

Before declaring the project complete:

- [ ] No core `TODO` functionality remains.
- [ ] No JPA entities are exposed directly.
- [ ] Backend authorization is enforced.
- [ ] No unbounded queries exist.
- [ ] Production does not use `ddl-auto=update`.
- [ ] All schema changes use Flyway.
- [ ] Security-critical paths have meaningful tests.
- [ ] Concurrency behavior is tested.
- [ ] IDOR protections are tested.
- [ ] Docker Compose starts the full stack.
- [ ] End-to-end workflow succeeds.
- [ ] Documentation is complete.
- [ ] `mvn clean verify` passes for every backend service.

---

# Completion Checklist

## Repository

- [ ] `AGENTS.md`
- [ ] Root `README.md`
- [ ] `.gitignore`
- [ ] `.env.example`
- [ ] `docker-compose.yml`
- [ ] `docs/`
- [ ] `requests/`

## Backend

- [ ] Auth Server
- [ ] Task Management Service
- [ ] KPI Service
- [ ] Independent Maven builds
- [ ] `com.parvez` package root

## Security

- [ ] OAuth2/OIDC
- [ ] PKCE
- [ ] JWT validation
- [ ] RSA signing
- [ ] Role/authority claims
- [ ] Backend authorization
- [ ] IDOR protection
- [ ] Security headers
- [ ] Rate limiting
- [ ] CORS
- [ ] CSRF decision documented
- [ ] Secure token/session storage

## Database

- [ ] PostgreSQL
- [ ] Flyway
- [ ] Hibernate validation
- [ ] Optimistic locking
- [ ] Required indexes
- [ ] Foreign keys
- [ ] Unique constraints
- [ ] No production `ddl-auto=update`
- [ ] No direct cross-service database access

## API

- [ ] DTOs
- [ ] Bean Validation
- [ ] Problem Details
- [ ] Pagination
- [ ] Filtering
- [ ] Sorting
- [ ] Authorization
- [ ] Idempotency
- [ ] Audit trail
- [ ] OpenAPI documentation

## Testing

- [ ] Unit tests
- [ ] Integration tests
- [ ] Testcontainers
- [ ] Security tests
- [ ] IDOR tests
- [ ] Concurrency tests
- [ ] API tests
- [ ] OIDC tests
- [ ] E2E workflow test
- [ ] `mvn clean verify` green

## Observability

- [ ] Actuator
- [ ] Micrometer
- [ ] Prometheus
- [ ] Structured JSON logs
- [ ] Trace IDs
- [ ] Request/correlation IDs
- [ ] Business metrics

## Frontend

- [ ] Shared design system
- [ ] OAuth2/OIDC + PKCE
- [ ] Task Management UI
- [ ] KPI UI
- [ ] Role-aware navigation
- [ ] Loading states
- [ ] Empty states
- [ ] Error states
- [ ] Charts

## DevOps

- [ ] Multi-stage Dockerfiles
- [ ] Non-root containers
- [ ] Health checks
- [ ] Docker Compose
- [ ] Persistent PostgreSQL volumes
- [ ] Environment-driven configuration
- [ ] Full-stack startup
- [ ] Frontend hosting / reverse proxy

## Documentation

- [ ] Architecture
- [ ] Security
- [ ] Database
- [ ] API
- [ ] Deployment
- [ ] Root README
- [ ] Service READMEs
- [ ] Development seed documentation
- [ ] Architectural decisions documented

```

This version is structured so it can be used directly as a **project backlog/specification `.md` file**, with consistent headings, task sections, requirements, and checkable acceptance criteria.
```