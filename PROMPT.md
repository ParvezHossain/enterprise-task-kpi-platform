Phase 0 — Analysis & Repository Setup

Prompt 1 — TICKET-0000: create AGENTS.md

Implement TICKET-0000 (Create AGENTS.md) from docs/tickets/backlog.md.

Create AGENTS.md at the repo root with:
- Package root for all Java code: com.parvez (never com.example)
- Stack: Java 25+, Spring Boot 4.0 (GA, Nov 2025), Spring Framework 7.0,
  Spring Security, Spring Authorization Server, Spring Data JPA, Hibernate,
  PostgreSQL, Flyway, Docker/Docker Compose, springdoc-openapi, Spring Boot
  Actuator, Micrometer, JUnit 6, Mockito, Testcontainers
- Confirm current stable dependency versions before adding them; do not copy
  older Spring Boot 2/3 tutorial config
- Never expose JPA entities via REST; always use request/response DTOs
- Backend must enforce all authorization — never trust frontend-only checks
- No `ddl-auto=update` in any non-local profile; all schema changes via Flyway
- Run the relevant `mvn clean verify` before considering any ticket done, and
  fix failures rather than leaving them
- I'll give you tickets one at a time from docs/tickets/backlog.md — implement
  only the ticket I name, don't jump ahead

Prompt 2 — TICKET-0001: scaffold repository skeleton

Implement TICKET-0001 (Inspect & scaffold repository skeleton) from
docs/tickets/backlog.md. Follow AGENTS.md for all stack/package conventions.
Show me the resulting directory tree when done.

Prompt 3 — TICKET-0002: architecture decisions

Implement TICKET-0002 (Decide & document core architectural choices) from
docs/tickets/backlog.md. Write docs/architecture.md with the decisions table.
For the KPI↔Task integration approach, default to a simple REST pull unless
you see a strong reason not to — I don't want Kafka/RabbitMQ for a learning
project of this size.
Phase 1 — Auth Server

Prompt 4 — TICKET-0101: Auth Server bootstrap

Implement TICKET-0101 (Auth Server project bootstrap) from
docs/tickets/backlog.md. Follow AGENTS.md conventions. Confirm the app boots
and /actuator/health returns 200 before finishing.

Prompt 5 — TICKET-0102: Auth database schema

Implement TICKET-0102 (Auth database schema, Flyway V1–V3) from
docs/tickets/backlog.md. Include Spring Authorization Server's required
tables (oauth2_registered_client, oauth2_authorization,
oauth2_authorization_consent) alongside users/roles/permissions. Run
flyway migrate against a fresh local Postgres and confirm it's clean before
finishing.

Prompt 6 — TICKET-0103: User/Role/Permission domain

Implement TICKET-0103 (User, Role, Permission domain + repositories) from
docs/tickets/backlog.md. Seed the four roles (ADMIN, PROJECT_MANAGER,
TEAM_LEADER, EMPLOYEE) and the permission list via a dev/test-only Flyway
data migration, clearly separated from production schema migrations. Add a
Testcontainers repository test confirming the seed data is queryable.

Prompt 7 — TICKET-0104: password hashing & user service

Implement TICKET-0104 (Password hashing & user registration/service layer)
from docs/tickets/backlog.md. Use Argon2id if it's cleanly supported by the
current Spring Security version; otherwise use the current Spring Security
default and document why in a code comment. Add a test confirming no
password or token value ever appears in application logs during
registration.

Prompt 8 — TICKET-0105: Spring Authorization Server config

Implement TICKET-0105 (Spring Authorization Server configuration — OIDC +
JWT) from docs/tickets/backlog.md. Use authorization code flow with PKCE,
refresh tokens, OIDC discovery, and RSA-signed JWTs loaded from config (not
hardcoded). Register two OAuth clients: task-management-ui and kpi-ui, via
the dev/test seed migration. Add the full flow as example requests in
requests/auth.http and confirm it works end to end.

Prompt 9 — TICKET-0106: login/logout/consent

Implement TICKET-0106 (Login/logout endpoints & consent handling) from
docs/tickets/backlog.md. If you auto-approve consent for the two first-party
clients registered in TICKET-0105, document in AGENTS.md or
docs/security.md why that's safe here (trusted first-party, not third-party
clients).

Prompt 10 — TICKET-0107: exception handling & Problem Details

Implement TICKET-0107 (Auth Server exception handling, validation, Problem
Details) from docs/tickets/backlog.md. Use RFC 9457 Problem Details format
for every error response. Add integration tests for invalid login and
malformed request bodies that assert the exact Problem Details shape.

Prompt 11 — TICKET-0108: Auth Server observability

Implement TICKET-0108 (Auth Server observability) from
docs/tickets/backlog.md. Secure /actuator/metrics and /actuator/prometheus
behind authentication, leave /actuator/health and /actuator/info open. Add
structured JSON logging with traceId/requestId.

Prompt 12 — TICKET-0109: Auth Server test suite

Implement TICKET-0109 (Auth Server test suite) from docs/tickets/backlog.md.
Use Testcontainers for Postgres in integration tests. Cover: full OIDC flow
success, invalid client, expired token, revoked refresh token. Run
mvn clean verify and fix any failures before finishing.

Prompt 13 — TICKET-0110: Auth Server Dockerfile

Implement TICKET-0110 (Auth Server Dockerfile + service README) from
docs/tickets/backlog.md. Multi-stage build, minimal JRE runtime image,
non-root user. Build the image locally and confirm it starts and passes
its health check before finishing.
Phase 2 — Task Management Service

Prompt 14 — TICKET-0201: Task Management bootstrap

Implement TICKET-0201 (Task Management project bootstrap) from
docs/tickets/backlog.md. Configure it as an OAuth2 resource server
validating JWTs against the Auth Server built in Phase 1 (issuer,
signature, expiry, audience). Confirm an unauthenticated request to a
protected endpoint returns 401 and a valid JWT reaches the controller.

Prompt 15 — TICKET-0201a: stub JWT for parallel dev (optional)

Implement TICKET-0201a (Stub JWT validation for parallel development) from
docs/tickets/backlog.md. Add a local-stub Spring profile only — make it
structurally impossible to enable via the docker or production profiles
(fail fast at startup if local-stub is active alongside either). Confirm
Task Management runs standalone against the stub before finishing.

(Skip this one if you're working strictly phase-by-phase and Auth Server is already done.)

Prompt 16 — TICKET-0202: Task database schema

Implement TICKET-0202 (Task database schema, Flyway) from
docs/tickets/backlog.md. Include the version column for optimistic locking
and the indexes listed in the ticket. Document the index rationale in
docs/database.md. Confirm flyway migrates clean and Hibernate validates.

Prompt 17 — TICKET-0203: Task domain + state machine

Implement TICKET-0203 (Task domain model + state machine) from
docs/tickets/backlog.md. Implement the transition matrix as a dedicated,
unit-testable component in the domain/application layer — not inside
controllers. Write unit tests covering every valid transition and asserting
every invalid one (e.g. DRAFT -> CLOSED directly) is rejected.

Prompt 18 — TICKET-0204: authorization policy layer

Implement TICKET-0204 (Authorization policy layer) from
docs/tickets/backlog.md. Build one dedicated policy component implementing
the full role x action authorization matrix — don't scatter @PreAuthorize
strings across controllers. Write a unit test covering every cell of the
matrix.

Prompt 19 — TICKET-0205: task creation

Implement TICKET-0205 (Task creation + DTOs + validation) from
docs/tickets/backlog.md. POST /api/v1/tasks restricted to Admin/PM. Never
expose JPA entities — request/response DTOs only. Add integration tests:
valid PM creation -> 201, Employee attempt -> 403, invalid payload -> 400
Problem Details.

Prompt 20 — TICKET-0206: approval & assignment

Implement TICKET-0206 (Task approval & assignment endpoints) from
docs/tickets/backlog.md. Use optimistic locking (the version column from
TICKET-0202) and return 409 on conflict. Write a genuine concurrency test —
two threads assigning the same task simultaneously via ExecutorService — and
assert exactly one succeeds.

Prompt 21 — TICKET-0207: status update, complete, close

Implement TICKET-0207 (Task status update, completion, close endpoints) from
docs/tickets/backlog.md. Enforce that an employee can only update their own
task — write an explicit IDOR test where Employee A tries to update
Employee B's task by ID and confirm it's rejected. Document whether you
return 403 or 404 for that case and why.

Prompt 22 — TICKET-0208: query endpoints, filtering, pagination

Implement TICKET-0208 (Task query endpoints — me/team/all + filtering +
pagination) from docs/tickets/backlog.md. Use Spring Data Specifications for
filtering. Enforce a configurable, server-side-capped max page size
regardless of what the client requests. Add integration tests confirming an
Employee never sees another user's tasks even via the broader query
endpoints they're not authorized for.

Prompt 23 — TICKET-0209: idempotency

Implement TICKET-0209 (Idempotency for approve/assign/close) from
docs/tickets/backlog.md, using the approach you documented in
docs/architecture.md under TICKET-0002. Make it DB-backed so it's correct
across multiple instances, not in-memory. Add a test replaying the same
idempotency key twice and asserting the transition only executes once.

Prompt 24 — TICKET-0210: audit trail

Implement TICKET-0210 (Immutable audit trail) from docs/tickets/backlog.md.
Populate TaskAuditEvent via a domain-event/observer pattern on every
transition. Expose no update or delete path for audit rows anywhere in the
service layer. Add a test confirming that.

Prompt 25 — TICKET-0211: exception handling, CORS, CSRF

Implement TICKET-0211 (Exception handling, Problem Details, CORS, CSRF
analysis) from docs/tickets/backlog.md. Configurable allowed-origins CORS
config, no wildcard. Document the CSRF decision explicitly in
docs/security.md (this is a stateless JWT resource server, so justify
whether CSRF protection is needed or not — don't just disable it silently).

Prompt 26 — TICKET-0212: observability & business metrics

Implement TICKET-0212 (Observability + business metrics) from
docs/tickets/backlog.md. Add Micrometer counters for
tasks.created/approved/assigned/completed/closed/overdue and request
duration timers. Confirm /actuator/prometheus shows the custom counters
after exercising the API manually.

Prompt 27 — TICKET-0213: full test suite

Implement TICKET-0213 (Task Management test suite — unit + integration +
security + concurrency) from docs/tickets/backlog.md. Concurrency tests must
run in parallel (ExecutorService/CountDownLatch), not sequentially. Run
mvn clean verify and fix any failures before finishing.

Prompt 28 — TICKET-0214: Dockerfile

Implement TICKET-0214 (Task Management Dockerfile + service README) from
docs/tickets/backlog.md, following the same pattern as TICKET-0110.
Phase 3 — KPI Service

Prompt 29 — TICKET-0301: KPI Service bootstrap

Implement TICKET-0301 (KPI Service project bootstrap) from
docs/tickets/backlog.md. Same Auth Server JWT issuer as Task Management, own
Postgres database — do not share the Task Management database.

Prompt 30 — TICKET-0302: KPI-Task integration

Implement TICKET-0302 (KPI <-> Task integration) from
docs/tickets/backlog.md, using the REST-pull approach decided in
docs/architecture.md. Do not let KPI Service query Task Management's
database directly. Add an integration test with a stubbed Task Management
endpoint, including a case where Task Management is unreachable, and confirm
KPI Service degrades gracefully rather than failing hard.

Prompt 31 — TICKET-0303: KPI aggregate queries

Implement TICKET-0303 (KPI aggregate queries) from docs/tickets/backlog.md.
Compute all aggregates (counts, completion %, on-time %, average completion
time) at the database via COUNT/SUM/AVG/GROUP BY — do not load entities into
Java and sum in a loop. Add a test with a seeded dataset asserting figures
match hand-computed expected values, and confirm via Hibernate statistics
that there's no N+1 query pattern.

Prompt 32 — TICKET-0304: scoring/ranking model

Implement TICKET-0304 (Deterministic scoring/ranking model) from
docs/tickets/backlog.md. Keep the formula as a pure, testable function
(completion rate, on-time rate, completed count, overdue penalty, priority
weighting). Document the exact formula in docs/api.md. Add unit tests with
fixed inputs asserting exact expected scores — it must be fully
deterministic, no randomness.

Prompt 33 — TICKET-0305: KPI REST endpoints

Implement TICKET-0305 (KPI REST endpoints + authorization) from
docs/tickets/backlog.md: GET /api/v1/kpis/me, /team, /company, /ranking,
/top-performers, /trends. Scope each so an Employee cannot pull
company-wide data. Add integration tests per role confirming correct
visibility and that list endpoints are paginated.

Prompt 34 — TICKET-0306: KPI caching

Implement TICKET-0306 (KPI caching or documented deferral) from
docs/tickets/backlog.md, per the decision already recorded in
docs/architecture.md under TICKET-0002. If deferring, write the "how to add
it later" note there rather than skipping the ticket silently.

Prompt 35 — TICKET-0307: KPI observability, tests, Dockerfile

Implement TICKET-0307 (KPI observability + test suite + Dockerfile) from
docs/tickets/backlog.md, following the same patterns as TICKET-0212,
TICKET-0213, and TICKET-0110/0214.
Phase 4 — Frontend

Prompt 36 — TICKET-0401: shared design system

Implement TICKET-0401 (Shared design system) from docs/tickets/backlog.md.
Tailwind-based, no framework beyond plain HTML5/JS. Build a static
style-guide page that renders every shared component (sidebar, top nav,
cards, tables, badges, modals, toasts, loading/empty/error states) so I can
review the look before it's wired to real data.

Prompt 37 — TICKET-0402: browser OAuth2/OIDC integration

Implement TICKET-0402 (Browser OAuth2/OIDC integration) from
docs/tickets/backlog.md, using the PKCE public-client (or BFF) approach
decided in docs/architecture.md. No client secret in JS. Document the token
storage decision explicitly and justify it against localStorage/XSS risk.

Prompt 38 — TICKET-0403: Task Management UI core pages

Implement TICKET-0403 (Task Management UI — core pages) from
docs/tickets/backlog.md: Login, Dashboard, My Tasks, Team Tasks, All Tasks,
Create Task, Task Details, Approval, Assignment, History, Profile.
Role-gate the navigation to match the authorization matrix, but remember
this is UX only — the backend already enforces the real access control.

Prompt 39 — TICKET-0404: KPI UI dashboards

Implement TICKET-0404 (KPI UI — dashboards) from docs/tickets/backlog.md:
KPI Dashboard, My/Team/Company Performance, Top Performers, Trend Analysis,
with charts for completion trend, completed-vs-overdue, ranking, and status
distribution. Use a lightweight chart library, not a full framework.
Phase 5 — Docker & Environment

Prompt 40 — TICKET-0501: docker-compose databases

Implement TICKET-0501 (docker-compose.yml — databases) from
docs/tickets/backlog.md, per the Postgres topology decided in
docs/architecture.md. Add health checks, named volumes, and .env-driven
credentials.

Prompt 41 — TICKET-0502: docker-compose application services

Implement TICKET-0502 (docker-compose.yml — application services) from
docs/tickets/backlog.md. Wire auth-server, task-management, kpi-service with
depends_on: condition: service_healthy. Run docker compose up --build and
confirm every service's /actuator/health returns UP before finishing.

Prompt 42 — TICKET-0503: docker-compose frontends

Implement TICKET-0503 (docker-compose.yml — frontends + reverse proxy) from
docs/tickets/backlog.md. Walk the full manual flow — login, create task,
approve, assign, complete, close, view KPI dashboard — entirely through
Docker Compose and confirm it works end to end before finishing.
Phase 6 — Cross-Cutting Hardening & Documentation

Prompt 43 — TICKET-0601: security headers & rate limiting

Implement TICKET-0601 (Security headers + rate limiting on auth endpoints)
from docs/tickets/backlog.md. Add CSP, X-Content-Type-Options,
Referrer-Policy, HSTS-when-TLS. Add basic rate limiting on the Auth Server
login endpoint and document its horizontal-scaling limitations if it's
in-memory.

Prompt 44 — TICKET-0602: request correlation

Implement TICKET-0602 (Request/response correlation across services) from
docs/tickets/backlog.md. Propagate a trace ID from the browser through
Task/KPI service logs. Show me one example: the same correlation ID
appearing in logs across services for a single create-task request.

Prompt 45 — TICKET-0603: end-to-end workflow test

Implement TICKET-0603 (End-to-end workflow test) from
docs/tickets/backlog.md. Automate the full workflow: create users -> auth ->
PM creates task -> Team Leader approves -> Team Leader assigns -> Employee
starts -> Employee completes -> PM closes -> KPI reflects completion. Run it
multiple times to confirm it's not flaky before finishing.

Prompt 46 — TICKET-0604: seed data

Implement TICKET-0604 (Seed data for local development) from
docs/tickets/backlog.md. One user per role, sample teams/projects/tasks with
enough history for the KPI charts to look meaningful. Clearly label it as
dev-only, never active in the production profile.

Prompt 47 — TICKET-0605: documentation pass

Implement TICKET-0605 (Documentation pass) from docs/tickets/backlog.md.
Finalize docs/architecture.md, docs/security.md, docs/database.md,
docs/api.md, docs/deployment.md, the root README.md, and each service's
README. Write it so a new developer following only the docs could get the
full stack running locally without asking me anything.

Prompt 48 — TICKET-0606: final engineering review

Implement TICKET-0606 (Final engineering review) from
docs/tickets/backlog.md. Walk through the checklist referenced in the
ticket (Architecture / Security / Database / REST / Performance / Testing /
Frontend / DevOps / Documentation) against the actual repo state, list what
fails each check, and fix it.