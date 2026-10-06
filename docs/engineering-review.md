# Engineering review

Reviewed on 2026-10-05 against PROMPT.md, the repository contract and the ticket
acceptance criteria. The implemented checklist passes; command results below are taken from executed
verification rather than inferred from source inspection.

## Implementation coverage

| Prompts | Result and implementation |
| --- | --- |
| 1–22 | Existing Auth and Task foundations reviewed; Auth identity timestamps, machine-client provisioning and bounded managed-team lookup completed where missing. |
| 23–24 | Transactional database command keys, replay across fresh service instances, real parallel duplicate tests, synchronous immutable audit observer and lifecycle timestamps. |
| 25–28 | Sanitized Problem Details, explicit CORS, after-commit business counters, overdue gauge, correlation, full Task verification and secure container. |
| 29–33 | Independent KPI application/database, authenticated bounded REST pull, atomic generation publication, SQL aggregates, deterministic scoring, role-scoped paginated APIs. |
| 34 | Explicitly resolved by the documented cache deferral; future Caffeine keys, TTL, authorization and generation invalidation are specified in architecture.md. |
| 35–39 | KPI tests/metrics/container, shared component guide, BFF OAuth/PKCE and refresh, Task pages and KPI charts. |
| 40–42 | Three separate databases, runtime/migration roles, health-ordered application services, static frontend reverse proxy and complete Compose workflow. |
| 43–46 | Security headers/login limiting, concrete create-request correlation, isolated real workflow repeats and explicit development-only historical seeds. |
| 47–48 | Current setup/API/security/database/deployment documents and this evidence-based review. |

## Checklist

| Area | Checks and evidence |
| --- | --- |
| Architecture | Three parent-less Maven projects; com.parvez packages; required Java/Boot/Framework/Security/JPA/PostgreSQL/Flyway/Actuator/Micrometer stack; compatibility sources and ADRs linked in architecture.md. No broker or unsupported dependency downgrade. |
| Security | Auth signature/issuer/expiry/client/grant/consent tests; Task and KPI audience enforcement; backend role/team/ownership restrictions and IDOR tests; current team authorization for KPI; machine scope separated from human privileges; Argon2 passwords and client hashes; no browser OAuth secrets/tokens. |
| Browser security | HttpOnly/Lax distinct app cookies; CSRF on BFF writes/logout; stateless bearer chain; configured server-side access-token refresh tested over HTTP; CSP/nosniff/referrer headers; secure-request HSTS; bounded configurable login limiter. |
| Database | Separate service databases and least-privilege runtime/migration users; additive Flyway migrations; Hibernate validate; optimistic versions; real concurrent winner/replay tests; audit SELECT/INSERT permission with UPDATE/DELETE denial. Applied migrations were not edited. |
| REST | Dedicated DTOs, consistent sanitized Problem Details and status codes, database references validated, explicit origin CORS, capped pages for task/history/reference/KPI lists and chart series. |
| Performance | Task specifications paginate in SQL. KPI COUNT/FILTER/AVG/GROUP BY runs in PostgreSQL; instrumentation asserts two summary SQL query calls regardless of task count. Feed uses UUID keyset pages capped at 500; scheduled work, lease duration and cleanup batches are bounded. |
| Integration | Confidential scoped machine credentials and finite HTTP timeouts; partial/failed/timed-out sweeps preserve the previous complete generation; database lease prevents overlapping publication; no direct cross-service database read. |
| Frontend | Plain HTML/JS, pinned Tailwind/Chart.js, role navigation plus route guards, escaped API strings, loading/empty/error/validation feedback, paginated lists, charts and keyboard/modal/mobile checks. Navigation during pending loading has a regression test. |
| DevOps | Pinned multi-stage images, JRE-only Java runtimes, UID/GID 10001, read-only application roots, dropped capabilities, health checks and named database volume. Existing CI verifies all three services and builds/smokes/publishes the tested Auth image. |
| Documentation | Root/service READMEs and architecture, development, API, security, database and deployment docs match current routes/configuration. Generated development material was tested afresh; paired key IDs/hashes remain consistent across standalone/Compose launch modes. |

## Remediation during review

The executed checks exposed and resolved these issues:

- Lifecycle timestamps were missing on transitions.
- Real Spring Security 7 machine JWT scopes are arrays; the feed now accepts the
  actual array contract and the interoperable space-separated form.
- Machine-client provisioning originally used the read-only runtime connection;
  it now uses the migration connection.
- Task uses URGENT priority; frontend, historical seeds and KPI weight 4 now agree.
- Browser navigation could miss a hash change while the first page was loading;
  listeners attach before loading and route rendering is serialized.
- Default BFF CSRF error dispatch lost its 403 status through the bearer chain;
  explicit sanitized access-denied handlers preserve the response.
- Persistence error messages could reveal implementation details; error branches
  now sanitize them and have HTTP regression coverage. Framework 404/405 statuses
  also remain intact through the generic error handlers.
- All KPI list-shaped contracts, including trends/distribution, now expose bounded
  page metadata.

No core placeholders or known unresolved implementation failures are accepted.
Rejected/reopened states are outside the existing five-state task lifecycle; every
supported transition is audited. Additional workflow states would require an
explicit domain/API/migration change.

## Verification

| Command/check | Result |
| --- | --- |
| mvn -f enterprise-platform/auth-server/pom.xml clean verify | PASS: 15 unit tests and 30 integration tests. |
| mvn -f enterprise-platform/task-management/pom.xml clean verify | PASS: 17 unit tests and 39 integration tests. |
| mvn -f enterprise-platform/kpi-service/pom.xml clean verify | PASS: 3 unit tests and 5 integration tests. |
| npm --prefix enterprise-platform/frontend run build | PASS: pinned Tailwind assets and local Chart.js bundle. |
| npm --prefix enterprise-platform/frontend test | PASS: 5 browser tests, including mobile chart layout and loading/navigation regression. |
| npm --prefix enterprise-platform/frontend run test:e2e -- --repeat-each=3 | PASS: three consecutive real workflow repeats against the final images, including live KPI chart data. |
| docker compose --env-file .local/stack.env -p enterprise-platform -f enterprise-platform/docker-compose.yml up --build -d --wait | PASS: all five services healthy. |
| python3 scripts/smoke-auth-container.py --image auth-server:local | PASS: health/HTTP UP, non-root JRE-only/read-only runtime and separate migration owner. |
| Container inspection and nginx -t | PASS: all four application/frontend containers run as 10001:10001 with read-only roots; Nginx configuration validates. |
| Fresh material generation and disposable database seed reapplication | PASS: shared signing ID and salted hashes agree; 80 tasks, two teams and four valid priorities persist without duplicates. |
| Maven dependency:tree for all three projects | PASS: Jupiter/Platform 6.0.3, Mockito 5.23.0 and Testcontainers 2.0.5 remain aligned. |
| Package scan, documentation local-link scan and git diff --check | PASS. |

All 109 Java tests passed with zero failures, errors or skips. Testcontainers use
real PostgreSQL; concurrency tests start simultaneous requests with latches.
BFF renewal tests exercise expired access tokens and a real HTTP refresh exchange.
The real business workflow additionally creates fresh users/team/project/task for
each repeat, authenticates four browser sessions, checks six audit actions and
backend CSRF rejection, observes the KPI count and renders the real chart data.

The existing GitHub Actions workflow was not changed; actionlint is therefore not
a required changed-workflow check. This report records local command execution,
not a claim that a remote CI run or production deployment was performed.

## Correlation evidence

The last verified browser create request used requestId f7b21149-e21f-426f-b784-20f3de697021.
Running python3 scripts/verify-correlation.py found two matching Task JSON
completion events (BFF and bearer API) and one KPI task_metric_staged event.
All three events carry the same requestId/traceId. KPI also records its independent
syncId, reflecting the asynchronous REST-pull boundary. The browser response
header supplied this identifier; no credential or token is included in the example.

## Operational limits

These are explicit design choices, with their production requirements documented:

- KPI is eventually consistent (normally a 60-second pull), and marks stale data.
  No aggregate-response cache is enabled; Prompt 34 permits documented deferral.
- BFF sessions and Auth login-rate windows are process-local. Scaling requires
  shared sessions/affinity and an ingress or distributed limiter.
- Local Compose binds only loopback HTTP ports. Production requires TLS, secure
  cookies, deliberately registered clients, provisioned secrets and database backup.
- Single signing-key replacement is not rolling multi-key rotation.
- BFF logout ends the app session; the independent Auth session and grants have
  the separate logout/revocation semantics explained in security.md.
