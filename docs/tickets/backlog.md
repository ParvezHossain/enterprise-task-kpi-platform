Phase 0 — Analysis & Repository Setup
TICKET-0000: Create AGENTS.md (stack contract)

Goal: Give Codex a persistent, repo-level contract so every future session (every ticket) enforces the same stack, package root, and non-negotiables without you re-typing them. Do: Create AGENTS.md at the repo root containing:

Package root: com.parvez for all Java code — never com.example.
Exact stack: Java 25+, Spring Boot 4+, Spring Framework 7+, Spring Security, Spring Authorization Server, Spring Data JPA, Hibernate, PostgreSQL, Flyway, Docker/Docker Compose, OpenAPI/springdoc, Spring Boot Actuator, Micrometer, JUnit 6, Mockito, Testcontainers.
"Verify current compatible versions before adding dependencies — do not copy older Spring Boot 2/3 tutorial config; if Spring Boot 4/Spring Framework 7 APIs differ from older docs, use the current API and note the change."
Non-negotiables from the master spec's "Important Working Rules" (no TODOs on core functionality, no exposed JPA entities, no frontend-only authorization, backend enforces all access control, no unbounded queries, no ddl-auto=update in production config, all schema changes via Flyway, run tests before declaring a ticket done).
Build/test commands per module (mvn clean verify, etc.) and where to update docs/*.md when a ticket makes an architectural decision. Acceptance: AGENTS.md exists at repo root; a fresh Codex session started with no other context still produces code using com.parvez and the correct stack versions.
TICKET-0001: Inspect & scaffold repository skeleton

Goal: Create the top-level repo structure with empty-but-valid modules. Do:

Create enterprise-platform/ with README.md, .gitignore, .env.example, docker-compose.yml (placeholder), requests/, docs/.
Create empty Maven parent-less projects for auth-server/, task-management/, kpi-service/ (each independently buildable — no shared parent POM unless you deliberately choose one and document why).
Create frontend/task-management-ui/ and frontend/kpi-ui/ with placeholder index.html.
Add a root .gitignore covering target/, .env, IDE files, node_modules/ (if ever used). Acceptance: tree enterprise-platform matches the README's structure; each backend folder has a valid empty pom.xml with correct groupId/artifactId.
TICKET-0002: Decide & document core architectural choices

Goal: Lock in decisions the rest of the backlog depends on, before writing service code. Do: Write docs/architecture.md with explicit decisions (and rejected alternatives) for:

Spring Boot / Spring Security / Spring Authorization Server exact versions (verify current compatible versions first).
One-Postgres-server-with-3-databases vs three Postgres containers for local dev.
KPI↔Task integration approach (REST pull vs event-driven read model) — pick the simplest, justify it.
Idempotency approach for approve/assign/close endpoints.
Caching approach for KPI aggregates (or explicitly: "none initially, here's how to add it"). Acceptance: docs/architecture.md exists with a decisions table (Decision / Chosen / Alternatives considered / Why).
Phase 1 — Auth Server
TICKET-0101: Auth Server project bootstrap

Do: Set up auth-server Spring Boot app with Spring Security, Spring Authorization Server, Spring Data JPA, PostgreSQL driver, Flyway, Actuator, validation, springdoc-openapi starters. Configure application.yml for local + docker profiles. Package base com.parvez.auth. Acceptance: App boots with an empty context; /actuator/health returns 200.

TICKET-0102: Auth database schema (Flyway V1–V3)

Do: Write Flyway migrations for users, roles, permissions, user_roles, role_permissions, plus Spring Authorization Server's required tables (oauth2_registered_client, oauth2_authorization, oauth2_authorization_consent). Add indexes on users.email, FK constraints, unique constraints. Set ddl-auto=validate. Acceptance: flyway migrate runs clean against a fresh DB; Hibernate validates entities against the schema with no mismatch.

TICKET-0103: User, Role, Permission domain + repositories

Do: JPA entities + Spring Data repositories for User, Role, Permission. Seed the four roles (ADMIN, PROJECT_MANAGER, TEAM_LEADER, EMPLOYEE) and the permission list from the spec via a Flyway data migration (dev/test only — separate from prod schema migrations, clearly labeled). Acceptance: Repository integration test (Testcontainers) confirms roles/permissions are seeded and queryable.

TICKET-0104: Password hashing & user registration/service layer

Do: Implement UserService with Argon2id (or current Spring Security default if Argon2id isn't supported cleanly — document the choice) password hashing. No raw password logging anywhere. Acceptance: Unit test confirms hashes differ per call (salted) and verify() works; a log-scanning test confirms no password/token strings appear in logs during a registration flow.

TICKET-0105: Spring Authorization Server configuration (OIDC + JWT)

Do: Configure AuthorizationServerConfig: authorization code flow with PKCE, refresh tokens, OIDC discovery endpoint, JWT access tokens signed with an RSA key (generated/loaded via config, not hardcoded). Register at least two OAuth clients (task-management-ui, kpi-ui) via the seed migration or a CommandLineRunner guarded to dev/test profiles. Acceptance: Manual .http request in requests/auth.http completes the full auth-code+PKCE flow against a running instance and returns a valid JWT; JWT claims include roles/authorities.

TICKET-0106: Login/logout endpoints & consent handling

Do: Implement the login form/endpoint, logout, and consent screen (or auto-approve for trusted first-party clients — document why that's safe here). Acceptance: End-to-end manual login → token → logout flow works.

TICKET-0107: Auth Server exception handling, validation, Problem Details

Do: @RestControllerAdvice returning RFC 9457 Problem Details for validation errors, bad credentials, and unexpected exceptions. No stack traces leaked. Acceptance: Integration tests hit invalid-login and malformed-request cases and assert Problem Details shape.

TICKET-0108: Auth Server observability

Do: Wire Actuator (health, info, metrics, prometheus), Micrometer, structured JSON logging with traceId/requestId. Secure actuator endpoints beyond health/info behind authentication. Acceptance: /actuator/prometheus exposes metrics when authenticated; unauthenticated access is rejected for sensitive endpoints.

TICKET-0109: Auth Server test suite

Do: Unit tests for password hashing, token issuance logic; integration tests (Testcontainers Postgres) for the full OIDC flow, invalid client, expired token, revoked refresh token scenarios. Acceptance: mvn clean verify green, meaningful coverage of security-critical paths (not just line coverage).

TICKET-0110: Auth Server Dockerfile + service README

Do: Multi-stage Dockerfile (build with Maven, run on minimal JRE, non-root user). auth-server/README.md explaining how to run standalone. Acceptance: docker build succeeds; container starts and passes health check locally.

Phase 2 — Task Management Service
TICKET-0201: Task Management project bootstrap

Do: Spring Boot app with Spring Security (OAuth2 resource server), Spring Data JPA, PostgreSQL, Flyway, Actuator, validation, springdoc. Configure it to validate JWTs issued by the Auth Server (issuer, signature, expiry, audience). Package base com.parvez.task. Acceptance: App boots; an unauthenticated request to a protected endpoint returns 401; a request with a valid JWT from auth-server reaches the controller.

TICKET-0202: Task database schema (Flyway)

Do: Migrations for tasks, teams, projects, task_audit_log, with version column for optimistic locking, FKs, indexes on assigned_to, team_id, status, created_at, due_date, project_id. Document index rationale in docs/database.md. Acceptance: Flyway migrates clean; Hibernate validates.

TICKET-0203: Task domain model + state machine

Do: Task aggregate (fields per spec), TaskStatus enum, an explicit state-machine component (Strategy/State pattern) encoding the valid transition matrix — implemented outside controllers, in the application/domain layer. Unit-testable in isolation. Acceptance: Unit tests cover every valid transition and reject every invalid one (e.g., DRAFT → CLOSED directly must fail).

TICKET-0204: Authorization policy layer

Do: A dedicated policy/authorization component (not scattered @PreAuthorize strings everywhere) implementing the authorization matrix from the README section 20. Method security annotations delegate to this layer. Acceptance: Unit tests for the policy layer cover every cell of the matrix (role × action → allow/deny).

TICKET-0205: Task creation + DTOs + validation

Do: POST /api/v1/tasks for Admin/PM only. Request/response DTOs (never expose entities), Jakarta Bean Validation on title/description/priority/project/team/dueDate. Acceptance: Integration test: PM creates a valid task → 201; Employee attempts → 403; invalid payload → 400 Problem Details.

TICKET-0206: Task approval & assignment endpoints

Do: POST /api/v1/tasks/{id}/approve (Team Leader / policy-gated), POST /api/v1/tasks/{id}/assign (validates employee exists, belongs to the team, Team Leader manages that team, task is in correct state). Use optimistic locking; return 409 on version conflict. Acceptance: Concurrency test: two Team Leaders assign the same task simultaneously — exactly one succeeds, the other gets 409.

TICKET-0207: Task status update, completion, close endpoints

Do: PATCH /api/v1/tasks/{id}/status (employee moves own task IN_PROGRESS → COMPLETED), POST /api/v1/tasks/{id}/close (PM/Admin only, COMPLETED → CLOSED). Acceptance: IDOR test: Employee A cannot update Employee B's task even by guessing the ID — 403/404 per your chosen disclosure policy (document which, and why).

TICKET-0208: Task query endpoints — me / team / all + filtering + pagination

Do: GET /api/v1/tasks, /me, /team/tasks, /{id}, /{id}/history with pagination (configurable max page size), Spring Data Specifications for filtering (status, priority, assignedTo, team, project, createdBy, date range), sorting. Acceptance: Integration tests confirm role-scoped visibility (Employee only sees own tasks even when hitting the "all" endpoint variants they're not authorized for) and that page size is capped server-side regardless of requested size.

TICKET-0209: Idempotency for approve/assign/close

Do: Implement the idempotency-key mechanism decided in TICKET-0002 (e.g., idempotency key stored with the audit row / a dedicated table, DB-backed so it survives across instances). Acceptance: Test: same idempotency key replayed twice → second call returns the original result without double-executing the transition.

TICKET-0210: Immutable audit trail

Do: TaskAuditEvent entity + repository, populated via a domain-event/observer pattern on every transition (created, approved, rejected, assigned, status changed, completed, closed, reopened). No update/delete path exposed for audit rows. Acceptance: Test confirms audit rows are created for each transition and that no service method allows editing them.

TICKET-0211: Exception handling, Problem Details, CORS, CSRF analysis

Do: @RestControllerAdvice for validation, auth, not-found, business-rule violations, optimistic-locking conflicts, DB constraint violations. Explicit CORS config (configurable allowed origins, no *). Document the CSRF decision in docs/security.md (stateless JWT resource server → likely CSRF-exempt, but state why explicitly). Acceptance: Tests cover each error branch returning correct status + Problem Details shape.

TICKET-0212: Observability + business metrics

Do: Actuator + Micrometer with counters for tasks.created/approved/assigned/completed/closed/overdue, request duration timers, correlation IDs propagated into logs. Acceptance: /actuator/prometheus shows the custom counters after exercising the API.

TICKET-0213: Task Management test suite (unit + integration + security + concurrency)

Do: Full suite per README section 65: state machine, authorization policy, repository/Testcontainers integration, IDOR checks, concurrency checks (parallel threads, not sequential). Acceptance: mvn clean verify green; concurrency tests demonstrably run in parallel (e.g., via ExecutorService/CountDownLatch) and assert exactly-one-winner semantics.

TICKET-0214: Task Management Dockerfile + service README
Phase 3 — KPI Service
TICKET-0301: KPI Service project bootstrap

Do: Spring Boot app, JWT resource server (same Auth Server issuer), own PostgreSQL database, Actuator, springdoc. Package base com.parvez.kpi.

TICKET-0302: KPI ↔ Task integration (per TICKET-0002 decision)

Do: Implement the chosen integration (e.g., a TaskClient REST adapter calling Task Management's read endpoints, or a scheduled read-model sync). Do not let KPI Service touch Task Management's database directly. Acceptance: Integration test with a stubbed/mocked Task Management endpoint confirms KPI Service builds its read model correctly and degrades gracefully if Task Management is unreachable.

TICKET-0303: KPI aggregate queries (DB-side, not in-memory)

Do: Native/derived aggregate queries (COUNT/SUM/AVG/GROUP BY) for: total assigned, completed, closed, overdue tasks; completion %; on-time %; average completion time — computed at the database, not by loading entities into Java and summing in a loop. Acceptance: Test with a seeded dataset confirms figures match hand-computed expected values; a query-count assertion (e.g., via Hibernate statistics) confirms no N+1.

TICKET-0304: Deterministic scoring/ranking model

Do: Implement the documented scoring formula (completion rate, on-time rate, completed count, overdue penalty, priority weighting) as a pure, testable function. Document the formula in docs/api.md. Acceptance: Unit tests with fixed inputs assert exact expected scores/rankings — no flakiness, no randomness.

TICKET-0305: KPI REST endpoints + authorization

Do: GET /api/v1/kpis/me|team|company|ranking|top-performers|trends, each authorization-scoped so Employees can't pull company-wide sensitive data. Acceptance: Integration tests per role confirm correct visibility scoping and pagination on list-shaped endpoints.

TICKET-0306: KPI caching (or documented deferral)

Do: Either implement caching with explicit TTL/invalidation for expensive aggregates, or write a clear "not yet, here's the plan" note in docs/architecture.md — per your TICKET-0002 decision.

TICKET-0307: KPI observability + test suite + Dockerfile
Phase 4 — Frontend
TICKET-0401: Shared design system

Do: Build the common Tailwind-based design tokens/components (sidebar, top nav, cards, tables, badges, modals, toasts, loading/empty/error states) used identically by both frontend apps. Put shared CSS/JS in a common location both apps reference. Acceptance: A static style-guide page renders every shared component.

TICKET-0402: Browser OAuth2/OIDC integration (PKCE, public client)

Do: Implement the login flow decided in TICKET-0002 (PKCE public client, or BFF — document whichever). No client secret in JS. Careful token storage decision, documented (avoid raw long-lived tokens in localStorage — justify whatever's chosen). Acceptance: Manual test: login → protected page loads with token → logout clears session.

TICKET-0403: Task Management UI — core pages

Do: Login, Dashboard, My Tasks, Team Tasks, All Tasks, Create Task, Task Details, Approval, Assignment, History, Profile — role-gated navigation matching the matrix (frontend gating is UX only; backend already enforces it). Acceptance: Manual walkthrough as each of the 4 roles shows only the pages/actions that role should see.

TICKET-0404: KPI UI — dashboards

Do: KPI Dashboard, My/Team/Company Performance, Top Performers, Trend Analysis, with charts (lightweight lib) for completion trend, completed-vs-overdue, ranking, status distribution. Acceptance: Manual walkthrough with seeded data shows correct numbers matching the KPI Service API responses.

Phase 5 — Docker & Environment
TICKET-0501: docker-compose.yml — databases

Do: Postgres service(s) per TICKET-0002's decision, with health checks, named volumes, and .env-driven credentials.

TICKET-0502: docker-compose.yml — application services

Do: Wire auth-server, task-management, kpi-service with depends_on: condition: service_healthy, correct networking, environment variables from .env.example. Acceptance: docker compose up --build brings up all services; each /actuator/health returns UP.

TICKET-0503: docker-compose.yml — frontends + reverse proxy (if chosen)

Do: Serve both frontend apps (static or via nginx), configure CORS-compatible origins end-to-end. Acceptance: Full manual login → create task → approve → assign → complete → close → KPI dashboard walkthrough works entirely through Docker Compose.

Phase 6 — Cross-Cutting Hardening & Documentation
TICKET-0601: Security headers + rate limiting on auth endpoints

Do: Configure security headers (CSP, X-Content-Type-Options, Referrer-Policy, HSTS-when-TLS). Add at least basic rate limiting on the Auth Server's login endpoint; document its horizontal-scaling limitations if it's in-memory, or push it to the reverse-proxy layer.

TICKET-0602: Request/response correlation across services

Do: Propagate a trace/request ID from browser → Task/KPI service → logs, so a single user action is traceable across all three services. Acceptance: A manual trace through logs for one create-task request shows the same correlation ID in all three services' logs (if the request touches all three).

TICKET-0603: End-to-end workflow test

Do: Automate the full workflow from README section 66 (create users → auth → create task → approve → assign → start → complete → close → KPI reflects it) as a single integration test, ideally spanning the real services via Testcontainers or a docker-compose-based test harness. Acceptance: The E2E test passes reliably (not flaky) in CI-like conditions.

TICKET-0604: Seed data for local development

Do: Dev-only seed script/migration creating one user per role, sample teams/projects/tasks with enough history for KPI charts to look meaningful. Clearly labeled as non-production.

TICKET-0605: Documentation pass

Do: Finalize docs/architecture.md, docs/security.md, docs/database.md, docs/api.md, docs/deployment.md, root README.md, and per-service READMEs per README section 71–72. Acceptance: A new developer following only the docs can get the full stack running locally without asking you anything.

TICKET-0606: Final engineering review

Do: Walk the checklist in README section 84 (Architecture / Security / Database / REST / Performance / Testing / Frontend / DevOps / Documentation) and fix anything unchecked.