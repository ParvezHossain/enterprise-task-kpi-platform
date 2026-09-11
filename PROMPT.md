# Ticket Implementation Prompts

## Phase 0 — Analysis & Repository Setup

### Prompt 1 — TICKET-0000: create AGENTS.md

Implement TICKET-0000 (Create AGENTS.md) from `docs/tickets/backlog.md`.

Create `AGENTS.md` at the repo root with:

- Package root for all Java code: `com.parvez` (never `com.example`)
- Stack: Java 25+, Spring Boot 4.0 (GA, Nov 2025), Spring Framework 7.0, Spring Security, Spring Authorization Server, Spring Data JPA, Hibernate, PostgreSQL, Flyway, Docker/Docker Compose, springdoc-openapi, Spring Boot Actuator, Micrometer, JUnit 6, Mockito, Testcontainers
- Confirm current stable dependency versions before adding them; do not copy older Spring Boot 2/3 tutorial configuration
- Never expose JPA entities via REST; always use request/response DTOs
- Backend must enforce all authorization — never trust frontend-only checks
- No `ddl-auto=update` in any non-local profile; all schema changes via Flyway
- Run the relevant `mvn clean verify` before considering any ticket done, and fix failures rather than leaving them
- I'll give you tickets one at a time from `docs/tickets/backlog.md` — implement only the ticket I name, don't jump ahead

---

### Prompt 2 — TICKET-0001: scaffold repository skeleton

Implement TICKET-0001 (Inspect & scaffold repository skeleton) from `docs/tickets/backlog.md`.

Inspect the existing repository and scaffold the project structure according to the architecture and conventions established by the repository.

Follow `AGENTS.md` for all stack, package, naming, and implementation conventions.

Create only the repository skeleton required by this ticket. Do not implement functionality belonging to later tickets.

Ensure:

- The repository structure supports the planned Auth Server, Task Management Service, KPI Service, and frontend components
- Java package roots use `com.parvez`
- The Maven project structure is valid and ready for subsequent implementation
- Standard source, test, resources, configuration, documentation, and request directories are created where required
- The structure is consistent with the decisions that will be documented in later architecture work

Verify the resulting repository structure is valid before finishing.

Show me the resulting directory tree when done.

---

### Prompt 3 — TICKET-0002: architecture decisions

Implement TICKET-0002 (Decide & document core architectural choices) from `docs/tickets/backlog.md`.

Create `docs/architecture.md` containing a clear decisions table covering the core architectural choices required by the project.

Document decisions for at least:

- Overall application architecture
- Service boundaries
- Auth Server responsibilities
- Task Management responsibilities
- KPI Service responsibilities
- Frontend architecture
- Authentication and authorization approach
- OAuth2/OIDC flow
- JWT strategy
- Database topology
- PostgreSQL usage
- Flyway migration strategy
- REST API conventions
- DTO strategy
- Optimistic locking
- Idempotency
- Audit trail
- Observability
- Testing strategy
- Docker/Docker Compose strategy
- Frontend authentication/token handling
- KPI ↔ Task integration
- KPI caching strategy

For the KPI ↔ Task integration approach:

- Default to a simple REST pull approach
- Do not introduce Kafka, RabbitMQ, or another message broker unless there is a strong documented reason
- Keep the solution appropriate for a learning project of this size

Document the reasoning behind each significant decision and identify any deliberate trade-offs.

Do not implement functionality belonging to later tickets.

Confirm `docs/architecture.md` exists and contains the completed decisions table before finishing.

---

# Phase 1 — Auth Server

### Prompt 4 — TICKET-0101: Auth Server bootstrap

Implement TICKET-0101 (Auth Server project bootstrap) from `docs/tickets/backlog.md`.

Create and configure the Auth Server according to the architecture and conventions established by `AGENTS.md` and `docs/architecture.md`.

Ensure:

- The Auth Server is a valid Spring Boot application
- Java package names use `com.parvez`
- Spring Boot, Spring Framework, and dependency versions are current and compatible
- Configuration is separated appropriately by environment/profile
- PostgreSQL connectivity is configured for the Auth Server
- Flyway is configured for database migrations
- Spring Boot Actuator is enabled
- `/actuator/health` is available according to the intended security configuration
- No functionality from later Auth Server tickets is implemented prematurely

Verify the application compiles and starts successfully.

Confirm:

- The application boots successfully
- `GET /actuator/health` returns HTTP `200`

Run the relevant Maven verification before finishing and fix any failures.

---

### Prompt 5 — TICKET-0102: Auth database schema

Implement TICKET-0102 (Auth database schema, Flyway V1–V3) from `docs/tickets/backlog.md`.

Create the Auth Server database schema using Flyway migrations.

Include:

- Users
- Roles
- Permissions
- User ↔ Role relationships
- Role ↔ Permission relationships
- `oauth2_registered_client`
- `oauth2_authorization`
- `oauth2_authorization_consent`

Use the schema required by the current Spring Authorization Server version rather than copying older examples.

Ensure:

- Migration versions are ordered and deterministic
- Production schema migrations contain schema changes only
- Foreign keys and constraints are appropriate
- Unique constraints are applied where required
- Timestamps and audit-related fields follow a consistent convention
- No `ddl-auto=update` is used for schema creation
- Hibernate is configured to validate rather than create or update the schema where appropriate

Run Flyway migration against a fresh local PostgreSQL database.

Confirm:

- All migrations execute successfully
- Flyway reports a clean migration state
- The required tables exist
- Hibernate schema validation succeeds

Fix any migration or schema-validation failures before finishing.

---

### Prompt 6 — TICKET-0103: User/Role/Permission domain

Implement TICKET-0103 (User, Role, Permission domain + repositories) from `docs/tickets/backlog.md`.

Create the User, Role, and Permission domain model and repository layer.

Ensure:

- Entities use the `com.parvez` package root
- JPA mappings match the Flyway schema
- Relationships are explicitly defined
- Repository interfaces use Spring Data JPA
- Sensitive fields such as password hashes are not exposed through REST
- The domain model is suitable for Spring Security integration

Seed the following roles:

- `ADMIN`
- `PROJECT_MANAGER`
- `TEAM_LEADER`
- `EMPLOYEE`

Seed the required permission list via a dev/test-only Flyway data migration.

Keep development/test seed data clearly separated from production schema migrations.

Do not make dev/test seed data active in production.

Add a Testcontainers repository integration test that:

- Starts a real PostgreSQL container
- Applies the required migrations
- Loads the seed data
- Queries the roles and permissions
- Confirms the seed data is correctly persisted and queryable

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 7 — TICKET-0104: password hashing & user service

Implement TICKET-0104 (Password hashing & user registration/service layer) from `docs/tickets/backlog.md`.

Implement the user registration and service layer using secure password handling.

Ensure:

- Passwords are never stored in plaintext
- Password hashes are generated using a secure password encoder
- Argon2id is used if it is cleanly supported by the current Spring Security version
- If Argon2id is not cleanly supported, use the current Spring Security default and document the reason in a code comment
- Password validation occurs in the service/application layer
- Registration logic does not expose password hashes
- Duplicate-user handling is deterministic and returns the appropriate application error
- Service methods are testable independently of controllers

Add tests covering:

- Successful registration
- Password hashing
- Password verification
- Duplicate registration
- Invalid registration data
- Confirmation that plaintext passwords are never persisted

Add a test confirming that no password or token value appears in application logs during registration.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 8 — TICKET-0105: Spring Authorization Server config

Implement TICKET-0105 (Spring Authorization Server configuration — OIDC + JWT) from `docs/tickets/backlog.md`.

Configure Spring Authorization Server using the current supported Spring Authorization Server APIs.

Implement:

- OAuth2 Authorization Code flow
- PKCE
- Refresh tokens
- OpenID Connect
- OIDC discovery
- RSA-signed JWT access/identity tokens
- JWT issuer configuration
- Appropriate token claims
- Secure client registration
- Authorization and token endpoints
- JWKS endpoint as required by OIDC/JWT validation

RSA signing keys must:

- Be loaded from configuration or an externalized key source
- Never be hardcoded into Java source
- Be configurable per environment
- Be suitable for local development and production deployment

Register these OAuth clients through the dev/test seed migration:

- `task-management-ui`
- `kpi-ui`

Do not hardcode development clients into application configuration when the ticket requires database-backed client registration.

Add complete example requests to `requests/auth.http` covering the authorization flow.

Include enough information to demonstrate:

- Authorization request
- PKCE parameters
- Login
- Authorization code
- Token exchange
- Refresh token
- OIDC discovery
- JWKS/token validation

Confirm the complete OAuth/OIDC flow works end to end before finishing.

---

### Prompt 9 — TICKET-0106: login/logout/consent

Implement TICKET-0106 (Login/logout endpoints & consent handling) from `docs/tickets/backlog.md`.

Implement the login, logout, and consent handling required by the Auth Server.

Ensure:

- Login uses the configured Spring Security authentication flow
- Logout invalidates the appropriate authentication/session state
- Consent handling follows the OAuth2/OIDC configuration
- Authentication failures are handled consistently
- The implementation does not bypass Spring Security's authentication mechanisms
- First-party client behavior is explicitly documented

If you auto-approve consent for:

- `task-management-ui`
- `kpi-ui`

then document the decision in `AGENTS.md` or `docs/security.md`.

The documentation must explain that auto-approval is considered safe in this project because these are trusted first-party clients rather than arbitrary third-party clients.

Do not apply automatic consent approval to untrusted third-party clients.

Add tests for the relevant login/logout/consent behavior.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 10 — TICKET-0107: exception handling & Problem Details

Implement TICKET-0107 (Auth Server exception handling, validation, Problem Details) from `docs/tickets/backlog.md`.

Implement centralized exception handling and validation for the Auth Server.

Use RFC 9457 Problem Details format for every applicable error response.

Ensure error responses consistently provide:

- HTTP status
- Problem type
- Title
- Detail
- Instance/request context where appropriate
- Validation errors where applicable

Handle at least:

- Validation failures
- Malformed JSON/request bodies
- Authentication failures
- Authorization failures
- Invalid OAuth requests
- Invalid client requests
- Resource-not-found errors
- Conflict errors
- Unexpected application errors

Do not expose stack traces, passwords, tokens, secrets, or internal implementation details in API responses.

Add integration tests for:

- Invalid login
- Malformed request body
- Validation failure
- Unauthorized request
- Invalid OAuth request where applicable

Tests must assert the exact Problem Details response shape rather than only checking the HTTP status.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 11 — TICKET-0108: Auth Server observability

Implement TICKET-0108 (Auth Server observability) from `docs/tickets/backlog.md`.

Configure production-appropriate observability for the Auth Server.

Secure:

- `/actuator/metrics`
- `/actuator/prometheus`

Leave open:

- `/actuator/health`
- `/actuator/info`

Ensure the exposed actuator surface is intentionally configured and does not expose sensitive information.

Add structured JSON logging.

Each request/log context should include:

- `traceId`
- `requestId`

Ensure correlation values remain consistent throughout the request lifecycle.

Do not log:

- Passwords
- Access tokens
- Refresh tokens
- Client secrets
- Authorization codes
- Other sensitive authentication material

Add tests where appropriate to confirm actuator security and logging behavior.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 12 — TICKET-0109: Auth Server test suite

Implement TICKET-0109 (Auth Server test suite) from `docs/tickets/backlog.md`.

Create a comprehensive Auth Server test suite covering unit, integration, security, OAuth2, and persistence behavior.

Use Testcontainers for PostgreSQL in integration tests.

Cover at minimum:

- Successful full OIDC flow
- Invalid OAuth client
- Invalid authentication
- Expired access token
- Revoked refresh token
- Authorization code flow
- PKCE validation
- Refresh token flow
- OIDC discovery
- JWT signature validation
- Database migrations
- User authentication

Ensure tests use isolated test data and do not depend on manually prepared local databases.

Run:

```
mvn clean verify
```

Fix every test, compilation, migration, configuration, or static-analysis failure before considering the ticket complete.

---

### Prompt 13 — TICKET-0110: Auth Server Dockerfile

Implement TICKET-0110 (Auth Server Dockerfile + service README) from `docs/tickets/backlog.md`.

Create a production-oriented Dockerfile for the Auth Server.

Use:

- A multi-stage build
- A minimal JRE runtime image
- A non-root runtime user
- No unnecessary build tools in the final image
- Appropriate JVM/container configuration
- A health-check strategy compatible with the application

Create or update the Auth Server service README with:

- Build instructions
- Run instructions
- Required environment variables
- Database configuration
- Health-check instructions
- Relevant ports
- Development notes
- Production considerations

Build the Docker image locally.

Start the container and confirm:

- The application starts successfully
- The container runs as a non-root user
- The application health check passes
- `/actuator/health` reports healthy

Fix any Docker or application startup failures before finishing.

---

# Phase 2 — Task Management Service

### Prompt 14 — TICKET-0201: Task Management bootstrap

Implement TICKET-0201 (Task Management project bootstrap) from `docs/tickets/backlog.md`.

Create and configure the Task Management Service according to `AGENTS.md` and `docs/architecture.md`.

Configure it as an OAuth2 Resource Server.

JWT validation must verify:

- Issuer
- Signature
- Expiry
- Audience

Use the Auth Server built in Phase 1 as the trusted issuer.

Ensure:

- Java packages use `com.parvez`
- The service has its own configuration
- PostgreSQL connectivity is configured
- Flyway is enabled for future migrations
- Actuator is configured
- Protected API endpoints require authentication
- JWT claims are mapped correctly to the application's security context

Add a minimal protected endpoint for verification.

Confirm:

- An unauthenticated request to a protected endpoint returns `401`
- A valid JWT is accepted
- A valid JWT reaches the controller/application layer

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 15 — TICKET-0201a: stub JWT for parallel dev (optional)

Implement TICKET-0201a (Stub JWT validation for parallel development) from `docs/tickets/backlog.md`.

This ticket is optional and should be skipped when working strictly phase-by-phase and the Auth Server is already available.

If implemented, add a dedicated local-stub Spring profile for development.

Ensure:

- Stub JWT validation is enabled only under the explicit local-stub profile
- The stub cannot accidentally be enabled in Docker
- The stub cannot accidentally be enabled in production
- Application startup fails fast if `local-stub` is active alongside a Docker or production profile
- Production authentication always uses real JWT validation
- The stub implementation is clearly isolated from production security configuration

Add tests confirming:

- The local-stub profile starts successfully
- A stub JWT is accepted
- The Docker/production profile combination fails fast
- Real JWT validation remains unchanged

Confirm Task Management can run standalone against the stub before finishing.

Run the relevant Maven verification and fix failures.

---

### Prompt 16 — TICKET-0202: Task database schema

Implement TICKET-0202 (Task database schema, Flyway) from `docs/tickets/backlog.md`.

Create the Task Management database schema using Flyway.

Implement all tables, relationships, constraints, and indexes required by the ticket.

Include:

- The `version` column required for optimistic locking
- All indexes specified in the ticket
- Appropriate foreign keys
- Appropriate uniqueness constraints
- Required timestamps
- Required task status/priority fields
- Required assignment/project/team relationships

Ensure:

- Schema creation happens only through Flyway
- Hibernate does not modify the schema
- Non-local profiles do not use `ddl-auto=update`
- Migration naming and ordering are deterministic

Document the rationale for every important index in:

`docs/database.md`

Run Flyway against a fresh PostgreSQL database.

Confirm:

- Flyway migration succeeds cleanly
- Hibernate schema validation succeeds
- Required indexes exist
- Required constraints exist

Fix any migration or validation failures before finishing.

---

### Prompt 17 — TICKET-0203: Task domain \+ state machine

Implement TICKET-0203 (Task domain model \+ state machine) from `docs/tickets/backlog.md`.

Implement the Task domain model and its lifecycle state machine.

The transition matrix must be implemented as a dedicated, unit-testable component in the domain/application layer.

Do not implement transition rules directly inside controllers.

Ensure:

- Valid transitions are explicitly defined
- Invalid transitions are rejected
- Transition rules are deterministic
- Controllers depend on the state-machine abstraction rather than duplicating rules
- Domain state cannot be changed through arbitrary controller logic

Write unit tests covering:

- Every valid transition
- Every invalid transition
- Boundary cases
- Direct invalid transitions such as `DRAFT -> CLOSED`

The tests must assert that every invalid transition is rejected.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 18 — TICKET-0204: authorization policy layer

Implement TICKET-0204 (Authorization policy layer) from `docs/tickets/backlog.md`.

Create one dedicated authorization policy component implementing the complete role × action authorization matrix.

Do not scatter authorization decisions across controllers.

Avoid using duplicated `@PreAuthorize` strings as the primary authorization model.

The policy layer must:

- Understand the application's roles
- Understand the application's task actions
- Evaluate the current authenticated user
- Evaluate the target task/resource where necessary
- Return deterministic authorization decisions
- Be independently unit-testable

Implement the full role × action matrix from the ticket.

Write a unit test covering every cell of the matrix.

Ensure authorization remains backend-enforced even when frontend role-gating is later added.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 19 — TICKET-0205: task creation

Implement TICKET-0205 (Task creation + DTOs \+ validation) from `docs/tickets/backlog.md`.

Implement task creation through:

`POST /api/v1/tasks`

Restrict task creation to:

- `ADMIN`
- `PROJECT_MANAGER`

Never expose JPA entities through REST.

Use:

- Request DTOs
- Response DTOs
- Explicit mapping between DTOs and domain/entities
- Bean validation where appropriate

Validate:

- Required fields
- Field lengths
- Allowed values
- Date relationships
- Priority/status constraints
- Other ticket-specific business rules

Ensure backend authorization is enforced independently of frontend behavior.

Add integration tests covering:

- Valid PM creation → `201`
- Valid Admin creation → `201`
- Employee attempt → `403`
- Invalid payload → `400`
- Invalid payload response uses RFC 9457 Problem Details

Ensure response bodies contain DTOs rather than JPA entities.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 20 — TICKET-0206: approval & assignment

Implement TICKET-0206 (Task approval & assignment endpoints) from `docs/tickets/backlog.md`.

Implement the task approval and assignment endpoints.

Use optimistic locking based on the `version` column introduced by TICKET-0202.

Ensure:

- Concurrent modifications are detected
- Stale versions cannot silently overwrite newer changes
- Optimistic locking conflicts return HTTP `409`
- Authorization is enforced through the dedicated policy layer
- Invalid state transitions are rejected
- DTOs are used for requests/responses
- No JPA entity is exposed through REST

Write a genuine concurrency test.

The test must:

- Create a task that can be assigned
- Start two threads using `ExecutorService`
- Attempt to assign the same task concurrently
- Coordinate execution so the operations genuinely overlap
- Assert exactly one assignment succeeds
- Assert the other operation receives the expected conflict behavior

Do not implement the concurrency test sequentially.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 21 — TICKET-0207: status update, complete, close

Implement TICKET-0207 (Task status update, completion, close endpoints) from `docs/tickets/backlog.md`.

Implement the task status update, completion, and close operations.

Ensure:

- State transitions follow the dedicated task state machine
- Authorization uses the dedicated policy component
- Employees can update only tasks assigned to themselves
- Users cannot manipulate another employee's task by changing the task ID
- Invalid transitions are rejected
- DTOs are used for API requests/responses
- Optimistic locking is respected

Write an explicit IDOR security test.

The test must:

- Create a task assigned to Employee B
- Authenticate as Employee A
- Attempt to update Employee B's task by ID
- Confirm the operation is rejected

Document in `docs/security.md` whether this case returns:

- `403 Forbidden`, or
- `404 Not Found`

Explain why that behavior was selected from a security and API-design perspective.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 22 — TICKET-0208: query endpoints, filtering, pagination

Implement TICKET-0208 (Task query endpoints — me/team/all \+ filtering + pagination) from `docs/tickets/backlog.md`.

Implement the required task query endpoints for:

- My tasks
- Team tasks
- All tasks

Implement filtering using Spring Data Specifications.

Support the ticket-defined filters while keeping filtering server-side.

Implement pagination.

Ensure:

- Pagination is performed by the database
- Clients cannot bypass authorization through query parameters
- The server enforces a configurable maximum page size
- Any requested page size above the configured maximum is capped server-side
- The maximum applies regardless of the client's requested value

Add integration tests confirming:

- Employees can see their own tasks
- Employees cannot see another employee's tasks
- Broader query endpoints do not bypass authorization
- PM/Team Leader/Admin visibility follows the authorization matrix
- Filtering works correctly
- Pagination works correctly
- Maximum page size is enforced

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 23 — TICKET-0209: idempotency

Implement TICKET-0209 (Idempotency for approve/assign/close) from `docs/tickets/backlog.md`.

Implement idempotency for:

- Approve
- Assign
- Close

Use the approach documented in `docs/architecture.md` under TICKET-0002.

The implementation must be DB-backed.

Do not use only in-memory state because correctness must hold across multiple application instances.

Ensure:

- Idempotency keys are validated
- Requests using the same key are treated consistently
- The original operation result can be returned for a replay
- Concurrent requests with the same key cannot execute the transition multiple times
- Idempotency state survives application-instance boundaries

Add a test that:

- Sends the same transition request twice with the same idempotency key
- Confirms the transition executes only once
- Confirms the second request receives the expected idempotent result

Add appropriate database constraints to prevent duplicate idempotency records.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 24 — TICKET-0210: audit trail

Implement TICKET-0210 (Immutable audit trail) from `docs/tickets/backlog.md`.

Implement an immutable task audit trail.

Populate `TaskAuditEvent` through a domain-event/observer pattern whenever a task transition occurs.

Audit events should capture the relevant information required by the ticket, including:

- Task
- Actor
- Previous state
- New state
- Action
- Timestamp
- Relevant metadata where appropriate

Ensure audit records are immutable.

There must be:

- No update path for audit records
- No delete path for audit records
- No service-layer method that allows audit mutation
- No REST endpoint that allows audit mutation

Expose audit information only through read operations where required by the ticket.

Add tests confirming:

- Every applicable transition creates an audit event
- Audit events contain the expected values
- Audit records cannot be updated through the service layer
- Audit records cannot be deleted through the service layer

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 25 — TICKET-0211: exception handling, CORS, CSRF

Implement TICKET-0211 (Exception handling, Problem Details, CORS, CSRF analysis) from `docs/tickets/backlog.md`.

Implement centralized exception handling for the Task Management Service.

Use RFC 9457 Problem Details for applicable error responses.

Handle consistently:

- Validation errors
- Authentication failures
- Authorization failures
- Not found errors
- State transition errors
- Optimistic locking conflicts
- Idempotency conflicts
- Malformed request bodies
- Unexpected application errors

Configure CORS using explicit, configurable allowed origins.

Ensure:

- No wildcard `*` is used for allowed origins
- Allowed origins are configurable by environment
- Credentials behavior is explicitly configured
- Methods and headers are intentionally restricted

Document the CSRF decision explicitly in:

`docs/security.md`

Explain the reasoning based on the architecture.

The service is a stateless JWT Resource Server, so explicitly justify whether CSRF protection is required or not.

Do not silently disable CSRF without documenting the security reasoning.

Add tests for Problem Details and CORS behavior where appropriate.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 26 — TICKET-0212: observability & business metrics

Implement TICKET-0212 (Observability + business metrics) from `docs/tickets/backlog.md`.

Implement observability for the Task Management Service.

Add Micrometer counters for:

- `tasks.created`
- `tasks.approved`
- `tasks.assigned`
- `tasks.completed`
- `tasks.closed`
- `tasks.overdue`

Add request-duration timers.

Ensure metrics:

- Are incremented only when the corresponding business operation succeeds
- Use consistent names/tags
- Do not expose sensitive information
- Are available through the configured Prometheus endpoint

Configure structured logging with request/trace correlation.

Exercise the API manually.

Confirm:

`/actuator/prometheus`

contains the custom counters after creating, approving, assigning, completing, closing, and/or otherwise exercising the applicable operations.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 27 — TICKET-0213: full test suite

Implement TICKET-0213 (Task Management test suite — unit + integration \+ security + concurrency) from `docs/tickets/backlog.md`.

Create a comprehensive Task Management test suite.

Cover:

- Domain logic
- State transitions
- Authorization policy
- Validation
- REST endpoints
- DTO mapping
- Database integration
- Flyway migrations
- JWT authentication
- Role authorization
- IDOR protection
- Pagination
- Filtering
- Optimistic locking
- Idempotency
- Audit events
- Observability where appropriate
- Concurrency

Use Testcontainers for PostgreSQL integration tests.

Concurrency tests must genuinely execute in parallel.

Use:

- `ExecutorService`
- `CountDownLatch`
- Or equivalent concurrency primitives according to an intentional ID can be followed from the browser request through configuration also kept the original ticket numbering and requirements intact while making every prompt follow the same implementation → requirements

Do not implement concurrency tests as sequential calls.

Ensure tests are deterministic and isolated.

Run:

```
mvn clean verify
```

Fix every failure before finishing.

---

### Prompt 28 — TICKET-0214: Dockerfile

Implement TICKET-0214 (Task Management Dockerfile \+ service README) from `docs/tickets/backlog.md`.

Create the Task Management Service Dockerfile following the same secure pattern as TICKET-0110.

Use:

- Multi-stage build
- Minimal JRE runtime image
- Non-root user
- No unnecessary build dependencies in the runtime image
- Container-aware JVM configuration
- Health-check support

Create or update the Task Management service README with:

- Build instructions
- Run instructions
- Configuration
- Environment variables
- Database setup
- Health checks
- Ports
- Local development instructions
- Production considerations

Build the image locally.

Start the container and confirm:

- The service starts
- The container runs as non-root
- Database connectivity works
- The health endpoint reports healthy

Fix all Docker/application failures before finishing.

---

# Phase 3 — KPI Service

### Prompt 29 — TICKET-0301: KPI Service bootstrap

Implement TICKET-0301 (KPI Service project bootstrap) from `docs/tickets/backlog.md`.

Create and configure the KPI Service according to `AGENTS.md` and `docs/architecture.md`.

Configure it to:

- Use the same Auth Server JWT issuer as Task Management
- Validate JWT issuer
- Validate JWT signature
- Validate JWT expiry
- Validate audience
- Use backend authorization
- Use its own PostgreSQL database

The KPI Service must have a separate database from Task Management.

Do not share the Task Management database directly.

Configure:

- Flyway
- Hibernate
- Spring Data JPA
- Actuator
- Micrometer
- PostgreSQL connectivity

Ensure package names use `com.parvez`.

Confirm the application starts successfully and database connectivity works.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 30 — TICKET-0302: KPI-Task integration

Implement TICKET-0302 (KPI \<-\> Task integration) from `docs/tickets/backlog.md`.

Implement KPI ↔ Task integration using the REST-pull approach documented in `docs/architecture.md`.

Do not allow KPI Service to query the Task Management database directly.

The integration must:

- Call Task Management through its REST API
- Use authenticated service-to-service communication where required
- Use DTOs for the external contract
- Handle Task Management failures gracefully
- Avoid cascading failures
- Apply appropriate timeouts
- Avoid indefinitely blocking KPI requests

Add an integration test using a stubbed Task Management endpoint.

The tests must cover:

- Successful Task Management response
- Empty/partial data where applicable
- Task Management unavailable
- Timeout/unreachable behavior

When Task Management is unreachable, confirm KPI Service:

- Does not fail hard unnecessarily
- Returns an appropriate degraded result or controlled error
- Does not corrupt persisted KPI data
- Logs the failure with correlation information

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 31 — TICKET-0303: KPI aggregate queries

Implement TICKET-0303 (KPI aggregate queries) from `docs/tickets/backlog.md`.

Implement KPI aggregate calculations at the database level.

Use database-side operations such as:

- `COUNT`
- `SUM`
- `AVG`
- `GROUP BY`
- Appropriate SQL expressions

Do not load large entity collections into Java and calculate aggregates through loops.

Compute at minimum:

- Counts
- Completion percentage
- On-time percentage
- Average completion time
- Other aggregates required by the ticket

Ensure the queries are efficient and appropriate for the expected dataset.

Add a test with a seeded dataset.

The test must:

- Seed known task data
- Calculate expected values manually
- Execute the actual aggregate queries
- Assert exact expected figures

Use Hibernate statistics or equivalent query instrumentation to confirm there is no N+1 query pattern.

Document or assert the expected query behavior where practical.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 32 — TICKET-0304: scoring/ranking model

Implement TICKET-0304 (Deterministic scoring/ranking model) from `docs/tickets/backlog.md`.

Implement the KPI scoring/ranking model as a pure, deterministic, testable function.

The formula must account for:

- Completion rate
- On-time rate
- Completed count
- Overdue penalty
- Priority weighting

Keep the formula independent of:

- Controllers
- HTTP
- Database access
- Randomness
- Current time unless explicitly passed as an input

Document the exact formula in:

`docs/api.md`

Include:

- Variables
- Weights
- Normalization
- Penalties
- Rounding rules
- Tie-breaking rules

Add unit tests using fixed inputs.

Tests must assert exact expected scores.

Ensure:

- Same inputs always produce the same score
- No randomness is used
- No hidden external state affects the result

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 33 — TICKET-0305: KPI REST endpoints

Implement TICKET-0305 (KPI REST endpoints \+ authorization) from `docs/tickets/backlog.md`.

Implement:

- `GET /api/v1/kpis/me`
- `GET /api/v1/kpis/team`
- `GET /api/v1/kpis/company`
- `GET /api/v1/kpis/ranking`
- `GET /api/v1/kpis/top-performers`
- `GET /api/v1/kpis/trends`

Apply the authorization policy defined by the project.

Ensure:

- Employees cannot retrieve company-wide data
- Users can access only the KPI scopes allowed for their role
- Backend authorization cannot be bypassed through query parameters
- Response data uses DTOs
- JPA entities are never exposed
- List endpoints are paginated
- Pagination has a server-side maximum page size

Add integration tests for each relevant role.

Tests must confirm:

- Correct visibility
- Correct authorization failures
- Employee restrictions
- Team/company boundaries
- Pagination behavior
- Ranking visibility
- Top-performer visibility
- Trend endpoint authorization

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 34 — TICKET-0306: KPI caching

Implement TICKET-0306 (KPI caching or documented deferral) from `docs/tickets/backlog.md`.

Follow the caching decision already documented in:

`docs/architecture.md`

If caching is implemented:

- Use the selected caching technology
- Define cache keys explicitly
- Define TTL/expiration behavior
- Define invalidation behavior
- Ensure stale data behavior is understood
- Ensure authorization boundaries cannot be bypassed through cached responses
- Add tests confirming cache behavior

If caching is intentionally deferred:

- Do not silently skip the ticket
- Update `docs/architecture.md`
- Explain why caching is currently deferred
- Document the exact approach that should be used later
- Document where caching should be introduced
- Document cache invalidation requirements
- Document any expected infrastructure changes

Confirm the ticket is explicitly resolved through implementation or documented deferral.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 35 — TICKET-0307: KPI observability, tests, Dockerfile

Implement TICKET-0307 (KPI observability + test suite + Dockerfile) from `docs/tickets/backlog.md`.

Implement KPI observability following the patterns established by:

- TICKET-0212
- TICKET-0213
- TICKET-0110
- TICKET-0214

Add appropriate:

- Micrometer metrics
- Request duration metrics
- Structured JSON logs
- Trace/request correlation
- Actuator configuration
- Integration tests
- Security tests
- Database tests
- KPI calculation tests
- REST tests
- Failure/degradation tests

Use Testcontainers for PostgreSQL integration tests.

Create a secure multi-stage Dockerfile with:

- Minimal JRE runtime
- Non-root user
- Health-check support

Create/update the KPI Service README.

Build the Docker image locally.

Confirm:

- Tests pass
- The service starts
- Database connectivity works
- Health check passes
- Prometheus metrics are available

Run `mvn clean verify` and fix all failures before finishing.

---

# Phase 4 — Frontend

### Prompt 36 — TICKET-0401: shared design system

Implement TICKET-0401 (Shared design system) from `docs/tickets/backlog.md`.

Create a shared frontend design system using:

- Tailwind CSS
- Plain HTML5
- Plain JavaScript

Do not introduce a frontend framework.

Create reusable styles/components for:

- Sidebar
- Top navigation
- Cards
- Tables
- Badges
- Buttons
- Forms
- Inputs
- Modals
- Toasts
- Loading states
- Empty states
- Error states
- Pagination
- Other components required by the ticket

Create a static style-guide page that renders every shared component.

The style-guide page must allow visual review before components are connected to real backend data.

Ensure:

- Consistent spacing
- Typography
- Colors
- Responsive behavior
- Accessible interaction states
- Keyboard-friendly controls
- Consistent error/success states

Do not wire the components to live API data in this ticket.

Confirm the style-guide page renders successfully before finishing.

---

### Prompt 37 — TICKET-0402: browser OAuth2/OIDC integration

Implement TICKET-0402 (Browser OAuth2/OIDC integration) from `docs/tickets/backlog.md`.

Implement browser authentication using the PKCE public-client or BFF approach selected in `docs/architecture.md`.

Ensure:

- No client secret is included in JavaScript
- Authorization Code + PKCE is used where the browser is the OAuth public client
- OIDC discovery is used where appropriate
- Redirect URIs are explicitly configured
- Authentication state is handled securely
- Logout is implemented correctly

Document the token-storage decision explicitly.

The documentation must explain the security trade-offs of:

- `localStorage`
- XSS risk
- Cookies
- HttpOnly cookies
- Memory-only storage
- BFF-based sessions where applicable

Do not store long-lived sensitive tokens in an unsafe location without documenting the security implications.

Confirm login, callback, authenticated API access, token expiration/refresh behavior, and logout work as designed.

---

### Prompt 38 — TICKET-0403: Task Management UI core pages

Implement TICKET-0403 (Task Management UI — core pages) from `docs/tickets/backlog.md`.

Implement the Task Management UI pages:

- Login
- Dashboard
- My Tasks
- Team Tasks
- All Tasks
- Create Task
- Task Details
- Approval
- Assignment
- History
- Profile

Use the shared design system from TICKET-0401.

Implement:

- API integration
- Loading states
- Empty states
- Error states
- Validation feedback
- Pagination
- Task filtering where applicable
- Authentication state handling

Role-gate frontend navigation according to the authorization matrix.

However:

- Treat frontend role-gating as UX only
- Never rely on it for security
- Backend authorization remains the source of truth

Ensure unauthorized users cannot gain access simply by manually navigating to a hidden URL.

Confirm the core task workflows work against the real backend APIs.

---

### Prompt 39 — TICKET-0404: KPI UI dashboards

Implement TICKET-0404 (KPI UI — dashboards) from `docs/tickets/backlog.md`.

Implement KPI pages for:

- KPI Dashboard
- My Performance
- Team Performance
- Company Performance
- Top Performers
- Trend Analysis

Add visualizations for:

- Completion trend
- Completed vs overdue
- Ranking
- Status distribution

Use a lightweight chart library.

Do not introduce a full frontend framework.

Ensure:

- Charts use API data
- Loading states are displayed
- Empty states are displayed
- API errors are handled
- Role visibility matches the backend authorization model
- Company-level data is not shown to unauthorized users
- Charts remain usable on supported screen sizes

Confirm KPI dashboards render correctly using realistic API data before finishing.

---

# Phase 5 — Docker & Environment

### Prompt 40 — TICKET-0501: docker-compose databases

Implement TICKET-0501 (docker-compose.yml — databases) from `docs/tickets/backlog.md`.

Create the Docker Compose database topology defined in `docs/architecture.md`.

Configure PostgreSQL databases according to the selected service topology.

Ensure:

- Auth Server has its intended database
- Task Management has its own database
- KPI Service has its own database
- Databases are not unintentionally shared
- Credentials are supplied through `.env`
- Secrets are not hardcoded into Compose
- Named volumes persist database data
- Health checks are configured
- Containers restart according to an intentional policy

Use appropriate PostgreSQL health checks.

Document:

- Database names
- Ports
- Credentials configuration
- Volume behavior
- Local development usage

Confirm all database containers start and report healthy.

---

### Prompt 41 — TICKET-0502: docker-compose application services

Implement TICKET-0502 (docker-compose.yml — application services) from `docs/tickets/backlog.md`.

Add these application services to Docker Compose:

- `auth-server`
- `task-management`
- `kpi-service`

Configure service dependencies using:

`depends_on: condition: service_healthy`

Ensure:

- Application services wait for required dependencies
- Environment variables are externalized
- Database URLs are correct
- Auth Server issuer configuration is correct
- Task Management trusts the Auth Server
- KPI Service trusts the Auth Server
- Service-to-service URLs resolve through Compose networking
- Health checks are available

Run:

```
docker compose up --build
```

Confirm every service starts successfully.

Verify:

- Auth Server `/actuator/health` returns `UP`
- Task Management `/actuator/health` returns `UP`
- KPI Service `/actuator/health` returns `UP`

Fix all build, networking, configuration, health-check, and startup failures before finishing.

---

### Prompt 42 — TICKET-0503: docker-compose frontends

Implement TICKET-0503 (docker-compose.yml — frontends + reverse proxy) from `docs/tickets/backlog.md`.

Add the frontend applications and reverse proxy to Docker Compose.

Configure the reverse proxy so the complete application can be exercised through the Docker Compose environment.

Ensure:

- Frontends can reach the required backend services
- OAuth redirect URIs work through the Compose environment
- CORS configuration matches the deployed origins
- API routes are correctly proxied
- Static frontend assets are served correctly
- Health checks are available where appropriate

Walk through the complete manual workflow entirely through Docker Compose:

1. Login
2. Create task
3. Approve task
4. Assign task
5. Start task
6. Complete task
7. Close task
8. View KPI dashboard

Confirm the entire workflow works end to end without bypassing the Docker Compose environment.

Fix all issues before finishing.

---

# Phase 6 — Cross-Cutting Hardening & Documentation

### Prompt 43 — TICKET-0601: security headers & rate limiting

Implement TICKET-0601 (Security headers + rate limiting on auth endpoints) from `docs/tickets/backlog.md`.

Implement security headers appropriate for the application.

Add:

- Content Security Policy (CSP)
- `X-Content-Type-Options`
- `Referrer-Policy`
- HSTS when TLS is enabled

Ensure headers are configured consistently and do not break the application's legitimate functionality.

Add basic rate limiting to the Auth Server login endpoint.

Ensure rate limiting:

- Limits repeated login attempts
- Does not expose sensitive information
- Has configurable thresholds
- Produces an appropriate response when exceeded

If rate limiting is in-memory:

- Document that limitation
- Explain that multiple Auth Server instances will not share the rate-limit state
- Explain the implications for horizontal scaling
- Document the appropriate future distributed solution

Add tests for the relevant headers and rate-limiting behavior.

Run the relevant Maven verification before finishing and fix failures.

---

### Prompt 44 — TICKET-0602: request correlation

Implement TICKET-0602 (Request/response correlation across services) from `docs/tickets/backlog.md`.

Implement request/response correlation across:

- Browser
- Auth Server where applicable
- Task Management
- KPI Service

Propagate a trace/correlation ID through the request lifecycle.

Ensure:

- Browser requests include or receive a correlation identifier
- Task Management logs the identifier
- KPI Service logs the identifier
- Service-to-service requests preserve correlation information
- Logs use structured JSON
- Correlation IDs do not contain sensitive information

Use the application's existing tracing/logging infrastructure where appropriate rather than creating duplicate mechanisms.

Show one concrete example of a single create-task request.

The example must demonstrate the same correlation ID appearing in logs across the relevant services.

Confirm the correlation ID can be followed from the browser request through the backend logs.

---

### Prompt 45 — TICKET-0603: end-to-end workflow test

Implement TICKET-0603 (End-to-end workflow test) from `docs/tickets/backlog.md`.

Automate the complete business workflow.

The test must perform:

1. Create users
2. Authenticate users
3. PM creates a task
4. Team Leader approves the task
5. Team Leader assigns the task
6. Employee starts the task
7. Employee completes the task
8. PM closes the task
9. KPI Service reflects the completed task

Use the real application components required by the ticket rather than mocking away the workflow.

Ensure authentication, authorization, database state, task transitions, audit events, and KPI integration are exercised.

Run the complete test multiple times.

Confirm:

- It passes repeatedly
- It is not flaky
- Database state is isolated between runs
- Timing assumptions do not create intermittent failures
- External dependencies are controlled appropriately

Fix all failures and flakiness before finishing.

---

### Prompt 46 — TICKET-0604: seed data

Implement TICKET-0604 (Seed data for local development) from `docs/tickets/backlog.md`.

Create realistic local-development seed data.

Include:

- One user per role
- `ADMIN`
- `PROJECT_MANAGER`
- `TEAM_LEADER`
- `EMPLOYEE`

Create sample:

- Teams
- Projects
- Tasks
- Assignments
- Status history
- Audit history
- KPI-relevant completion/overdue data

Provide enough historical variation for KPI charts to look meaningful.

Ensure the seed data demonstrates:

- Different task statuses
- Different priorities
- Completed tasks
- Overdue tasks
- On-time tasks
- Multiple employees
- Multiple teams
- Different ranking outcomes

Clearly label the seed data as development-only.

Ensure it is never active in the production profile.

Use an explicit development/test mechanism rather than accidentally placing production seed data into normal schema migrations.

Confirm a clean local environment can be populated with the seed data successfully.

---

### Prompt 47 — TICKET-0605: documentation pass

Implement TICKET-0605 (Documentation pass) from `docs/tickets/backlog.md`.

Finalize the project's documentation.

Update:

- `docs/architecture.md`
- `docs/security.md`
- `docs/database.md`
- `docs/api.md`
- `docs/deployment.md`
- Root `README.md`
- Auth Server README
- Task Management README
- KPI Service README
- Frontend README/documentation where applicable

Ensure the documentation accurately reflects the actual repository state.

Document:

- Architecture
- Service responsibilities
- Package structure
- Authentication
- Authorization
- OAuth2/OIDC
- JWT validation
- Database topology
- Flyway migrations
- Schema management
- API endpoints
- DTO conventions
- Error handling
- Problem Details
- CORS
- CSRF decision
- Security headers
- Rate limiting
- Observability
- Metrics
- Logging
- Correlation IDs
- Testing
- Testcontainers
- Docker
- Docker Compose
- Environment variables
- Seed data
- Frontend setup
- Local development
- Production considerations
- Troubleshooting

The root README must allow a new developer to follow the documentation and get the complete stack running locally without needing to ask for undocumented steps.

Verify every documented command and important configuration detail against the actual repository.

Fix documentation inconsistencies before finishing.

---

### Prompt 48 — TICKET-0606: final engineering review

Implement TICKET-0606 (Final engineering review) from `docs/tickets/backlog.md`.

Perform a complete engineering review of the repository against the checklist referenced by the ticket.

Review:

- Architecture
- Security
- Database
- REST APIs
- Performance
- Testing
- Frontend
- DevOps
- Documentation

Compare each requirement against the actual repository state rather than assuming previous tickets were implemented correctly.

Create a review report identifying:

- What passes
- What fails
- What is partially implemented
- What is inconsistent with `AGENTS.md`
- What is inconsistent with `docs/architecture.md`
- What requires remediation

For every failed or incomplete check:

- Fix the implementation
- Update tests where required
- Update documentation where required
- Re-run the relevant verification

Pay particular attention to:

- Backend authorization
- IDOR protection
- JWT validation
- OAuth2/OIDC correctness
- Secret handling
- Password handling
- Flyway migrations
- Hibernate schema validation
- Optimistic locking
- Idempotency
- Audit immutability
- Problem Details
- CORS
- CSRF reasoning
- Security headers
- Rate limiting
- Database query efficiency
- N+1 queries
- Pagination limits
- Concurrency correctness
- Observability
- Docker security
- Non-root containers
- Frontend/backend security boundaries
- Production profile configuration
- Dev-only seed data
- Documentation accuracy

Run the complete verification suite.

Run:

```
mvn clean verify
```

Run the complete Docker Compose validation where applicable.

Do not leave known failures unresolved.

The ticket is complete only when the final engineering checklist passes against the actual repository state.

This is ready to save as **`ticket-implementation-prompts.md`**. I also kept the original ticket numbering and requirements intact while making every prompt follow the same implementation → requirements → tests → verification pattern as Prompt 1.