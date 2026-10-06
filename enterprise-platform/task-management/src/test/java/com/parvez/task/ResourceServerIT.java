package com.parvez.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@org.springframework.context.annotation.Import(ResourceServerIT.FailureFixtures.class)
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceServerIT {
    private static final String ISSUER = "https://auth.example.test";
    private static final KeyPair SIGNING_KEYS = generateKeyPair();
    private static final String PUBLIC_JWK_SET = publicJwkSet();
    private static HttpServer jwkServer;
    private static final String JWK_SET_URI = startJwkServer();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.context.ApplicationContext context;

    @AfterAll
    static void stopJwkServer() {
        if (jwkServer != null) {
            jwkServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.flyway.placeholders.runtimeRole", POSTGRES::getUsername);
        properties.add("task.query.maximum-page-size", () -> 2);
        properties.add("task.security.jwt.issuer", () -> ISSUER);
        properties.add("task.security.jwt.jwk-set-uri", () -> JWK_SET_URI);
    }

    @Test
    void rejectsRequestsWithoutBearerAuthentication() throws Exception {
        HttpResponse<String> response = get(null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void acceptsValidAuthServerJwtAndPassesItToController() throws Exception {
        HttpResponse<String> response = get(token(ISSUER, "task-management", Instant.now().plusSeconds(60)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("integration-user");
    }

    @Test
    void rejectsJwtWithWrongIssuer() throws Exception {
        HttpResponse<String> response = get(token("https://other.example.test", "task-management", Instant.now().plusSeconds(60)));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsJwtWithWrongAudience() throws Exception {
        HttpResponse<String> response = get(token(ISSUER, "kpi-service", Instant.now().plusSeconds(60)));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        HttpResponse<String> response = get(token(ISSUER, "task-management", Instant.now().minusSeconds(60)));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsUnsignedLocalStubJwtWithoutTheLocalStubProfile() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("local-stub")
                .subject("integration-user")
                .audience("task-management")
                .issueTime(java.util.Date.from(Instant.now().minusSeconds(1)))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(60)))
                .build();

        HttpResponse<String> response = get(new PlainJWT(claims).serialize());

        assertThat(response.statusCode()).isEqualTo(401);
    }

        @Test
        void projectManagerCanCreateTaskAndReceivesResponseDto() throws Exception {
        UUID teamId = createProject();
        UUID projectId = jdbcTemplate.queryForObject("SELECT id FROM projects WHERE team_id = ?", UUID.class, teamId);
            UUID projectManagerId = UUID.randomUUID();
        String requestBody = taskRequest("Review API", "HIGH", projectId, teamId);

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                List.of("PROJECT_MANAGER"), projectManagerId.toString()), requestBody);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("location").orElseThrow()).contains("/api/v1/tasks/");
        assertThat(response.body()).contains("\"title\":\"Review API\"", "\"status\":\"DRAFT\"",
            "\"priority\":\"HIGH\"", "\"version\":0");
        assertThat(response.body()).doesNotContain("startedAt", "completedAt", "passwordHash");
        }

        @Test
        void adminCanCreateTask() throws Exception {
        UUID teamId = createProject();
        UUID projectId = jdbcTemplate.queryForObject("SELECT id FROM projects WHERE team_id = ?", UUID.class, teamId);
            UUID adminId = UUID.randomUUID();

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                List.of("ADMIN"), adminId.toString()), taskRequest("Admin task", "MEDIUM", projectId, teamId));

        assertThat(response.statusCode()).isEqualTo(201);
        }

        @Test
        void employeeCannotCreateTask() throws Exception {
        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("EMPLOYEE")), taskRequest("Employee task", "LOW", UUID.randomUUID(), UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertProblemDetail(response, "Forbidden");
        }

        @Test
        void invalidTaskRequestReturnsValidationProblemDetail() throws Exception {
        String invalidRequest = """
            {"title":"  ","description":"valid","priority":"HIGH",
             "projectId":"%s","teamId":"%s"}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER")), invalidRequest);

        assertThat(response.statusCode()).isEqualTo(400);
        assertProblemDetail(response, "Invalid request");
        assertThat(response.body()).contains("\"errors\"", "\"title\"");
        }

        @Test
        void invalidPriorityReturnsProblemDetail() throws Exception {
        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER")), taskRequest("Task", "INVALID", UUID.randomUUID(), UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertProblemDetail(response, "Invalid request");
        }

        @Test
        void rejectsProjectFromAnotherTeam() throws Exception {
        UUID requestedTeamId = createTeam();
        UUID otherTeamId = createTeam();
        UUID otherProjectId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?)",
            otherProjectId, otherTeamId, "other-team-project");

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER")), taskRequest("Task", "HIGH", otherProjectId, requestedTeamId));

        assertThat(response.statusCode()).isEqualTo(400);
        assertProblemDetail(response, "Invalid task reference");
        }

        @Test
        void teamLeaderCanApproveTaskInManagedTeam() throws Exception {
        TaskWorkflow workflow = createDraftTask();

        HttpResponse<String> response = approve(workflow);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"APPROVED\"", "\"version\":1");
        }

        @Test
        void teamLeaderCannotApproveTaskOutsideManagedTeam() throws Exception {
        TaskWorkflow workflow = createDraftTask();
        String unrelatedLeaderToken = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                    List.of("TEAM_LEADER"), UUID.randomUUID().toString());

        HttpResponse<String> response = post(unrelatedLeaderToken,
            "/api/v1/tasks/" + workflow.taskId() + "/approve", "{\"version\":0}");

        assertThat(response.statusCode()).isEqualTo(403);
        assertProblemDetail(response, "Forbidden", "/api/v1/tasks/" + workflow.taskId() + "/approve");
        }

        @Test
        void assignmentRequiresTeamMembershipAndApprovedUnassignedTask() throws Exception {
        TaskWorkflow workflow = createDraftTask();
        String leaderToken = leaderToken(workflow);
        HttpResponse<String> beforeApproval = post(leaderToken,
            "/api/v1/tasks/" + workflow.taskId() + "/assign",
            assignmentRequest(workflow.employeeOneId(), 0));
        assertThat(beforeApproval.statusCode()).isEqualTo(409);

        HttpResponse<String> approved = post(leaderToken,
            "/api/v1/tasks/" + workflow.taskId() + "/approve", "{\"version\":0}");
        assertThat(approved.statusCode()).isEqualTo(200);

        HttpResponse<String> notMember = post(leaderToken,
            "/api/v1/tasks/" + workflow.taskId() + "/assign",
            assignmentRequest(UUID.randomUUID(), 1));
        assertThat(notMember.statusCode()).isEqualTo(400);
        assertProblemDetail(notMember, "Invalid task reference", "/api/v1/tasks/" + workflow.taskId() + "/assign");

        HttpResponse<String> assigned = post(leaderToken,
            "/api/v1/tasks/" + workflow.taskId() + "/assign",
            assignmentRequest(workflow.employeeOneId(), 1));
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(assigned.body()).contains(workflow.employeeOneId().toString(), "\"version\":2");
        }

        @Test
        void concurrentAssignmentsAllowOneWinnerAndRejectTheStaleVersion() throws Exception {
        TaskWorkflow workflow = createDraftTask();
        String leaderToken = leaderToken(workflow);
        assertThat(approve(workflow).statusCode()).isEqualTo(200);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> first = executor.submit(() -> assignAfterBarrier(
                workflow, workflow.employeeOneId(), leaderToken, ready, start));
            Future<HttpResponse<String>> second = executor.submit(() -> assignAfterBarrier(
                workflow, workflow.employeeTwoId(), leaderToken, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS).statusCode(),
                second.get(10, TimeUnit.SECONDS).statusCode());

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        }

        @Test
        void employeeCanStartAndCompleteOwnAssignedTaskAndManagerCanCloseIt() throws Exception {
        TaskWorkflow workflow = createAssignedTask();
        String employeeToken = employeeToken(workflow.employeeTwoId());

        HttpResponse<String> started = patch(employeeToken, workflow.taskId(),
            statusRequest("IN_PROGRESS", 2));
        assertThat(started.statusCode()).isEqualTo(200);
        assertThat(started.body()).contains("\"status\":\"IN_PROGRESS\"", "\"version\":3");

        HttpResponse<String> completed = patch(employeeToken, workflow.taskId(),
            statusRequest("COMPLETED", 3));
        assertThat(completed.statusCode()).isEqualTo(200);
        assertThat(completed.body()).contains("\"status\":\"COMPLETED\"", "\"version\":4");

        HttpResponse<String> closed = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER"),workflow.projectManagerId().toString()), "/api/v1/tasks/" + workflow.taskId() + "/close", "{\"version\":4}");
        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(closed.body()).contains("\"status\":\"CLOSED\"", "\"version\":5");
        }

        @Test
        void employeeCannotUpdateAnotherEmployeesTaskById() throws Exception {
        TaskWorkflow workflow = createAssignedTask();
        HttpResponse<String> started = patch(employeeToken(workflow.employeeTwoId()), workflow.taskId(),
            statusRequest("IN_PROGRESS", 2));
        assertThat(started.statusCode()).isEqualTo(200);

        HttpResponse<String> idorAttempt = patch(employeeToken(workflow.employeeOneId()), workflow.taskId(),
            statusRequest("COMPLETED", 3));

        assertThat(idorAttempt.statusCode()).isEqualTo(404);
        assertProblemDetail(idorAttempt, "Task not found", "/api/v1/tasks/" + workflow.taskId() + "/status");
        }

        @Test
        void rejectsInvalidAndStaleEmployeeStatusTransitionsWithConflict() throws Exception {
        TaskWorkflow workflow = createAssignedTask();
        String employeeToken = employeeToken(workflow.employeeTwoId());

        HttpResponse<String> invalidTransition = patch(employeeToken, workflow.taskId(),
            statusRequest("COMPLETED", 2));
        assertThat(invalidTransition.statusCode()).isEqualTo(409);
        assertProblemDetail(invalidTransition, "Task conflict", "/api/v1/tasks/" + workflow.taskId() + "/status");

        HttpResponse<String> staleVersion = patch(employeeToken, workflow.taskId(),
            statusRequest("IN_PROGRESS", 1));
        assertThat(staleVersion.statusCode()).isEqualTo(409);
        assertProblemDetail(staleVersion, "Task conflict", "/api/v1/tasks/" + workflow.taskId() + "/status");
        }

        @Test
        void queryEndpointsEnforceRoleScopesAndServerPageSizeCap() throws Exception {
        QueryFixture fixture = insertQueryFixture();
        String managerToken = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER"), fixture.creatorId().toString());
        String employeeToken = employeeToken(fixture.employeeOneId());
        String leaderToken = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("TEAM_LEADER"), fixture.leaderId().toString());

        HttpResponse<String> allFirstPage = get(managerToken,
            "/api/v1/tasks?createdBy=" + fixture.creatorId() + "&size=999&sortBy=TITLE&direction=ASC");
        assertThat(allFirstPage.statusCode()).isEqualTo(200);
        assertThat(allFirstPage.body()).contains("\"size\":2", "\"totalElements\":4", "A assigned");
        assertThat(allFirstPage.body().indexOf("A assigned"))
            .isLessThan(allFirstPage.body().indexOf("A other team"));

        String adminToken = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("ADMIN"), UUID.randomUUID().toString());
        HttpResponse<String> adminAll = get(adminToken,
            "/api/v1/tasks?createdBy=" + fixture.creatorId() + "&size=999");
        assertThat(adminAll.statusCode()).isEqualTo(200);
        assertThat(adminAll.body()).contains("\"totalElements\":4", "\"size\":2");

        HttpResponse<String> allSecondPage = get(managerToken,
            "/api/v1/tasks?createdBy=" + fixture.creatorId() + "&page=1&size=999&sortBy=TITLE&direction=ASC");
        assertThat(allSecondPage.body()).contains("\"page\":1", "\"size\":2", "B assigned", "Unassigned");

        HttpResponse<String> mine = get(employeeToken, "/api/v1/tasks/me?size=999");
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).contains("\"size\":2", "\"totalElements\":2", "A assigned", "A other team");
        assertThat(get(employeeToken, "/api/v1/tasks").statusCode()).isEqualTo(403);

        HttpResponse<String> team = get(leaderToken,
            "/api/v1/tasks/team?teamId=" + fixture.teamOneId() + "&size=999");
        assertThat(team.statusCode()).isEqualTo(200);
        assertThat(team.body()).contains("\"totalElements\":3", "\"size\":2");
        assertThat(get(leaderToken, "/api/v1/tasks/team?teamId=" + fixture.teamTwoId()).statusCode())
            .isEqualTo(403);
        }

        @Test
        void taskSpecificationsApplyEveryDocumentedFilter() throws Exception {
        QueryFixture fixture = insertQueryFixture();
        String managerToken = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER"), fixture.creatorId().toString());
        String creator = "createdBy=" + fixture.creatorId();

        assertQueryHasOne(managerToken, "/api/v1/tasks?" + creator + "&status=COMPLETED");
        assertQueryHasOne(managerToken, "/api/v1/tasks?" + creator + "&priority=URGENT");
        assertQueryHasOne(managerToken, "/api/v1/tasks?" + creator + "&assignedTo=" + fixture.employeeTwoId());
        assertQueryHasOne(managerToken, "/api/v1/tasks?" + creator + "&teamId=" + fixture.teamTwoId());
        assertQueryHasOne(managerToken, "/api/v1/tasks?" + creator + "&projectId=" + fixture.projectTwoId());
        assertQueryHasOne(managerToken, "/api/v1/tasks?" + creator
            + "&createdFrom=2026-01-02T00:00:00Z&createdTo=2026-01-02T23:59:59Z");
        assertQueryHasOne(managerToken, "/api/v1/tasks?createdBy=" + fixture.otherCreatorId());
        }

        @Test
        void rejectsReversedCreationDateRangeWithProblemDetail() throws Exception {
        QueryFixture fixture = insertQueryFixture();
        String managerToken = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER"), fixture.creatorId().toString());

        HttpResponse<String> response = get(managerToken,
            "/api/v1/tasks?createdFrom=2026-01-03T00:00:00Z&createdTo=2026-01-02T00:00:00Z");

        assertThat(response.statusCode()).isEqualTo(400);
        assertProblemDetail(response, "Invalid query");
        }

        @Test
        void ownTaskQueryCannotBeWidenedByAssigneeFilterAndDetailsRespectVisibility() throws Exception {
        QueryFixture fixture = insertQueryFixture();
        String employeeToken = employeeToken(fixture.employeeOneId());

        HttpResponse<String> widened = get(employeeToken,
            "/api/v1/tasks/me?assignedTo=" + fixture.employeeTwoId());
        assertThat(widened.statusCode()).isEqualTo(200);
        assertThat(widened.body()).contains("\"totalElements\":0");

        assertThat(get(employeeToken, "/api/v1/tasks/" + fixture.employeeOneTaskId()).statusCode())
            .isEqualTo(200);
        assertThat(get(employeeToken, "/api/v1/tasks/" + fixture.employeeTwoTaskId()).statusCode())
            .isEqualTo(404);
        }

        @Test
        void taskHistoryIsVisibleOnlyToUsersWhoCanReadTheTaskAndIsPaged() throws Exception {
        QueryFixture fixture = insertQueryFixture();
        String employeeToken = employeeToken(fixture.employeeOneId());

        HttpResponse<String> history = get(employeeToken,
            "/api/v1/tasks/" + fixture.employeeOneTaskId() + "/history?size=999");
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.body()).contains("\"size\":2", "\"totalElements\":1", "\"action\":\"CREATED\"");
        assertThat(get(employeeToken, "/api/v1/tasks/" + fixture.employeeTwoTaskId() + "/history").statusCode())
            .isEqualTo(404);
        }

    @Test
    void queryParametersCannotBypassRolesAndInvalidPaginationIsRejected() throws Exception {
        QueryFixture fixture = insertQueryFixture();
        String employee = employeeToken(fixture.employeeOneId());
        String leader = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                List.of("TEAM_LEADER"), fixture.leaderId().toString());
        for (String role : List.of("ADMIN", "PROJECT_MANAGER")) {
            String privileged = token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                    List.of(role), fixture.creatorId().toString());
            assertThat(get(privileged, "/api/v1/tasks/team?teamId=" + fixture.teamTwoId()).statusCode())
                    .isEqualTo(200);
            assertThat(get(privileged, "/api/v1/tasks/" + fixture.employeeTwoTaskId()).statusCode())
                    .isEqualTo(200);
        }
        assertThat(get(employee, "/api/v1/tasks/team?teamId=" + fixture.teamOneId()
                + "&assignedTo=" + fixture.employeeOneId()).statusCode()).isEqualTo(403);
        assertThat(get(leader, "/api/v1/tasks?teamId=" + fixture.teamOneId()).statusCode()).isEqualTo(403);
        assertThat(get(leader, "/api/v1/tasks/" + fixture.employeeOneOtherTeamTaskId()).statusCode())
                .isEqualTo(403);
        assertThat(get(leader, "/api/v1/tasks/" + fixture.employeeOneOtherTeamTaskId() + "/history")
                .statusCode()).isEqualTo(403);
        assertThat(get(leader, "/api/v1/tasks/" + fixture.employeeTwoTaskId()).statusCode()).isEqualTo(200);
        assertThat(get(leader, "/api/v1/tasks/team").statusCode()).isEqualTo(400);
        for (String query : List.of("page=-1", "size=0", "size=-1", "page=2147483647&size=2",
                "size=abc", "sortBy=assignedTo", "direction=INVALID", "status=INVALID",
                "priority=INVALID", "assignedTo=invalid", "createdFrom=invalid")) {
            HttpResponse<String> response = get(employee, "/api/v1/tasks/me?" + query);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.headers().firstValue("content-type").orElseThrow())
                    .contains("application/problem+json");
        }
        HttpResponse<String> capped = get(employee, "/api/v1/tasks/me?size=2147483647");
        assertThat(capped.statusCode()).isEqualTo(200);
        assertThat(capped.body()).contains("\"size\":2");
        assertThat(get(employee, "/api/v1/tasks/me").body()).contains("\"size\":2");
        assertThat(get(employee, "/api/v1/tasks/me?page=10").body())
                .contains("\"content\":[]", "\"totalElements\":2");
    }

        @Test
        void migrationCreatesRequiredTaskIndexesAndForeignKeys() {
        var taskIndexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND tablename = 'tasks'",
            String.class);
            var schemaIndexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() "
                    + "AND tablename IN ('team_memberships', 'team_leaderships', 'task_audit_log')",
                String.class);
            var schemaForeignKeys = jdbcTemplate.queryForList(
            "SELECT conname FROM pg_constraint WHERE connamespace = current_schema()::regnamespace AND contype = 'f'",
            String.class);

        assertThat(taskIndexes).contains(
            "idx_tasks_assigned_to", "idx_tasks_team_id", "idx_tasks_status",
            "idx_tasks_created_at", "idx_tasks_due_date", "idx_tasks_project_id", "idx_tasks_created_by");
        assertThat(schemaIndexes).contains("idx_team_memberships_user_id", "idx_team_leaderships_user_id",
            "idx_task_audit_log_task_occurred_at");
        assertThat(schemaForeignKeys).contains(
            "fk_team_memberships_team", "fk_team_leaderships_membership", "fk_projects_team", "fk_tasks_team",
            "fk_tasks_project_team", "fk_tasks_assignee_team", "fk_task_audit_log_task");
        }

        @Test
        void rejectsTaskAssignedToUserOutsideItsTeam() {
        UUID taskTeamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        insertTeam(taskTeamId);
        insertTeam(otherTeamId);
        jdbcTemplate.update("INSERT INTO team_memberships (team_id, user_id) VALUES (?, ?)", otherTeamId, userId);
        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?)",
            projectId, taskTeamId, "task-team-project");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO tasks (id, title, status, priority, assigned_to, team_id, project_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), "task", "DRAFT", "MEDIUM",
            userId, taskTeamId, projectId))
            .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void rejectsProjectOutsideTaskTeam() {
        UUID taskTeamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        insertTeam(taskTeamId);
        insertTeam(otherTeamId);
        jdbcTemplate.update("INSERT INTO team_memberships (team_id, user_id) VALUES (?, ?)", taskTeamId, userId);
        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?)",
            projectId, otherTeamId, "other-team-project");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO tasks (id, title, status, priority, assigned_to, team_id, project_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), "task", "DRAFT", "MEDIUM",
            userId, taskTeamId, projectId))
            .isInstanceOf(DataIntegrityViolationException.class);
        }

        private void insertTeam(UUID teamId) {
        jdbcTemplate.update("INSERT INTO teams (id, name) VALUES (?, ?)", teamId, "team-" + teamId);
        }

    private HttpResponse<String> get(String token) throws Exception {
        return get(token, "/api/v1/whoami");
    }

    private HttpResponse<String> get(String token, String path) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void identicalKeyReplaysOneCommittedTransitionAndAuditEvent() throws Exception {
        TaskWorkflow workflow=createDraftTask();String key=UUID.randomUUID().toString();
        String path="/api/v1/tasks/"+workflow.taskId()+"/approve";
        var first=keyPost(leaderToken(workflow),path,"{\"version\":0}",key);
        var replay=keyPost(leaderToken(workflow),path,"{\"version\":0}",key);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM task_audit_log WHERE task_id=? AND action='APPROVED'",Long.class,workflow.taskId())).isEqualTo(1);
        assertThat(keyPost(leaderToken(workflow),path,"{\"version\":1}",key).statusCode()).isEqualTo(409);
        assertThat(keyPost(leaderToken(workflow),path,"{\"version\":0}",null).statusCode()).isEqualTo(400);
        jdbcTemplate.update("DELETE FROM team_leaderships WHERE team_id=? AND user_id=?",workflow.teamId(),workflow.leaderId());
        assertThat(keyPost(leaderToken(workflow),path,"{\"version\":0}",key).statusCode()).isEqualTo(403);
    }
    @Test
    void persistedIdempotencyResponseReplaysThroughFreshCommandInstance() throws Exception {
        TaskWorkflow workflow=createDraftTask();String key=UUID.randomUUID().toString(),jwt=leaderToken(workflow);
        var first=keyPost(jwt,"/api/v1/tasks/"+workflow.taskId()+"/approve",new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("version",0)),key);
        assertThat(first.statusCode()).isEqualTo(200);
        var json=context.getBean(tools.jackson.databind.ObjectMapper.class);
        var fresh=new com.parvez.task.service.IdempotentTaskCommands(jdbcTemplate,json,
            context.getBean(com.parvez.task.service.TaskCommandService.class),
            context.getBean(com.parvez.task.persistence.TaskRepository.class),
            context.getBean(com.parvez.task.security.CurrentTaskActorProvider.class),
            context.getBean(com.parvez.task.authorization.TaskAuthorizationPolicy.class));
        var transaction=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var decoded=context.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class).decode(jwt);
        var replay=transaction.execute(status->fresh.execute(decoded,workflow.taskId(),"approve",key,0,null));
        assertThat(replay).isEqualTo(json.readValue(first.body(),com.parvez.task.web.TaskResponse.class));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM task_audit_log WHERE task_id=? AND action='APPROVED'",Long.class,workflow.taskId())).isEqualTo(1);
    }
    @Test
    void concurrentIdenticalKeysCommitExactlyOnce() throws Exception {
        TaskWorkflow workflow=createDraftTask();String key=UUID.randomUUID().toString();
        String path="/api/v1/tasks/"+workflow.taskId()+"/approve";String jwt=leaderToken(workflow);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> operation=()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException();return keyPost(jwt,path,"{\"version\":0}",key);};
            Future<HttpResponse<String>> first=executor.submit(operation),second=executor.submit(operation);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            var one=first.get(15,TimeUnit.SECONDS);var two=second.get(15,TimeUnit.SECONDS);
            assertThat(one.statusCode()).isEqualTo(200);assertThat(two.statusCode()).isEqualTo(200);assertThat(two.body()).isEqualTo(one.body());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM task_audit_log WHERE task_id=? AND action='APPROVED'",Long.class,workflow.taskId())).isEqualTo(1);
    }
    @Test
    void failedCommandRollsBackKeyAndExpiredReplayUsesDomainRules() throws Exception {
        TaskWorkflow workflow=createDraftTask();String key=UUID.randomUUID().toString();String path="/api/v1/tasks/"+workflow.taskId()+"/approve";
        assertThat(keyPost(leaderToken(workflow),path,"{\"version\":9}",key).statusCode()).isEqualTo(409);
        assertThat(keyPost(leaderToken(workflow),path,"{\"version\":0}",key).statusCode()).isEqualTo(200);
        jdbcTemplate.update("UPDATE task_command_keys SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 day' WHERE command_key=?",key);
        assertThat(keyPost(leaderToken(workflow),path,"{\"version\":0}",key).statusCode()).isEqualTo(409);
    }
    @Test
    void fullLifecycleWritesImmutableHistoryAndBusinessMetrics() throws Exception {
        TaskWorkflow workflow=createAssignedTask();
        assertThat(patch(employeeToken(workflow.employeeTwoId()),workflow.taskId(),statusRequest("IN_PROGRESS",2)).statusCode()).isEqualTo(200);
        assertThat(patch(employeeToken(workflow.employeeTwoId()),workflow.taskId(),statusRequest("COMPLETED",3)).statusCode()).isEqualTo(200);
        String manager=token(ISSUER,"task-management",Instant.now().plusSeconds(60),List.of("PROJECT_MANAGER"),workflow.projectManagerId().toString());
        assertThat(keyPost(manager,"/api/v1/tasks/"+workflow.taskId()+"/close","{\"version\":4}",UUID.randomUUID().toString()).statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForList("SELECT action FROM task_audit_log WHERE task_id=? ORDER BY occurred_at",String.class,workflow.taskId())).containsExactly("CREATED","APPROVED","ASSIGNED","STARTED","COMPLETED","CLOSED");
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at IS NOT NULL AND started_at IS NOT NULL AND closed_at IS NOT NULL FROM tasks WHERE id=?",Boolean.class,workflow.taskId())).isTrue();
        assertThat(java.util.Arrays.stream(com.parvez.task.persistence.TaskAuditLogRepository.class.getMethods()).map(java.lang.reflect.Method::getName)).noneMatch(name->name.startsWith("delete")||name.startsWith("save"));
        var metrics=get(manager,"/actuator/prometheus");assertThat(metrics.statusCode()).isEqualTo(200);assertThat(metrics.body()).contains("tasks_total","tasks_completed_total","tasks_overdue");
    }
    @Test
    void correlationCorsAndSecurityErrorsAreConsistent() throws Exception {
        String id=UUID.randomUUID().toString();
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tasks")).header("X-Request-ID",id).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);assertThat(response.body()).contains("\"status\":401","\"instance\":\"/api/v1/tasks\"");
        assertThat(response.headers().firstValue("X-Request-ID")).contains(id);
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        var preflight=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tasks")).header("Origin","http://127.0.0.1:8080").header("Access-Control-Request-Method","POST").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(preflight.statusCode()).isEqualTo(200);assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).contains("http://127.0.0.1:8080");
        var forbidden=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tasks")).header("Origin","https://evil.example").header("Access-Control-Request-Method","POST").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(forbidden.statusCode()).isEqualTo(403);assertThat(forbidden.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }
    @Test
    void auditRuntimePrivilegesBlockUpdatesAndDeletes() throws Exception {
        String role="audit_test_"+UUID.randomUUID().toString().replace("-","");
        jdbcTemplate.execute("CREATE ROLE "+role);
        jdbcTemplate.execute("GRANT USAGE ON SCHEMA public TO "+role);
        jdbcTemplate.execute("GRANT SELECT, INSERT ON task_audit_log TO "+role);
        try(var connection=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var statement=connection.createStatement()) {
            statement.execute("SET ROLE "+role);
            try {
                assertThatThrownBy(()->statement.executeUpdate("UPDATE task_audit_log SET action='TAMPERED'")).isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(()->statement.executeUpdate("DELETE FROM task_audit_log")).isInstanceOf(java.sql.SQLException.class);
            } finally { statement.execute("RESET ROLE"); }
        } finally { jdbcTemplate.execute("DROP OWNED BY "+role);jdbcTemplate.execute("DROP ROLE "+role); }
    }
    @Test
    void titleSearchEscapesWildcardsAndKeepsCreatorScope() throws Exception {
        TaskWorkflow workflow=createDraftTask();
        String jwt=token(ISSUER,"task-management",Instant.now().plusSeconds(60),List.of("PROJECT_MANAGER"),workflow.projectManagerId().toString());
        assertThat(get(jwt,"/api/v1/tasks?createdBy="+workflow.projectManagerId()+"&title=Workflow").body()).contains("\"totalElements\":1");
        assertThat(get(jwt,"/api/v1/tasks?createdBy="+workflow.projectManagerId()+"&title=%25").body()).contains("\"totalElements\":0");
    }

    @Test
    void machineFeedAcceptsArrayScopesAndRejectsHumanOrMutationAccess() throws Exception {
        var key=new RSAKey.Builder((RSAPublicKey)SIGNING_KEYS.getPublic()).privateKey((RSAPrivateKey)SIGNING_KEYS.getPrivate()).keyID("integration-key").build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var claims=JwtClaimsSet.builder().issuer(ISSUER).subject("kpi-sync").audience(List.of("task-management")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("scope",List.of("task.metrics.read")).claim("roles",List.of()).build();
        String jwt=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("integration-key").build(),claims)).getTokenValue();
        var feed=get(jwt,"/internal/metrics/tasks?size=10000");
        assertThat(feed.statusCode()).isEqualTo(200);assertThat(feed.body()).contains("\"upperId\"","\"nextCursor\"").doesNotContain("description");
        assertThat(get(token(ISSUER,"task-management",Instant.now().plusSeconds(60),List.of("ADMIN")),"/internal/metrics/tasks").statusCode()).isEqualTo(403);
        TaskWorkflow workflow=createDraftTask();
        assertThat(post(jwt,taskRequest("Machine mutation","HIGH",UUID.randomUUID(),workflow.teamId())).statusCode()).isEqualTo(403);
    }

    @Test
    void unexpectedAndPersistenceErrorsRemainSanitizedProblemDetails() throws Exception {
        String admin=token(ISSUER,"task-management",Instant.now().plusSeconds(60),List.of("ADMIN"));
        assertThat(get(admin,"/api/v1/no-such-route").statusCode()).isEqualTo(404);
        assertThat(keyPost(admin,"/api/v1/tasks/me","{}",UUID.randomUUID().toString()).statusCode()).isEqualTo(405);
        for(String failure:List.of("unexpected","integrity","optimistic")) {
            var response=get(admin,"/test/errors/"+failure);
            assertThat(response.statusCode()).isEqualTo(failure.equals("unexpected")?500:409);
            var object=new tools.jackson.databind.ObjectMapper().readTree(response.body());
            assertThat(object.size()).isEqualTo(5);
            assertThat(response.body()).doesNotContain("private-value","SELECT","TaskEntity","stackTrace");
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        }
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class FailureFixtures {
        @org.springframework.context.annotation.Bean FailureController failureController(){return new FailureController();}
    }
    @org.springframework.web.bind.annotation.RestController
    static class FailureController {
        @org.springframework.web.bind.annotation.GetMapping("/test/errors/{failure}")
        Object fail(@org.springframework.web.bind.annotation.PathVariable String failure) {
            switch(failure) {
                case "integrity" -> throw new DataIntegrityViolationException("SELECT private-value");
                case "optimistic" -> throw new org.springframework.orm.ObjectOptimisticLockingFailureException("TaskEntity private-value",UUID.randomUUID());
                default -> throw new IllegalStateException("private-value");
            }
        }
    }

    private HttpResponse<String> keyPost(String jwt,String path,String body,String key) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer "+jwt).header("Content-Type","application/json");
        if(key!=null) request.header("Idempotency-Key",key);
        return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String token, String body) throws Exception {
        return post(token, "/api/v1/tasks", body);
    }

    private HttpResponse<String> post(String token, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private TaskWorkflow createDraftTask() throws Exception {
        UUID teamId = createTeam();
        UUID projectId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID employeeOneId = UUID.randomUUID();
        UUID employeeTwoId = UUID.randomUUID();
        UUID projectManagerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?)",
                projectId, teamId, "project-" + projectId);
        jdbcTemplate.update("INSERT INTO team_memberships (team_id, user_id) VALUES (?, ?), (?, ?), (?, ?)",
                teamId, leaderId, teamId, employeeOneId, teamId, employeeTwoId);
        jdbcTemplate.update("INSERT INTO team_leaderships (team_id, user_id) VALUES (?, ?)", teamId, leaderId);

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER"), projectManagerId.toString()),
            taskRequest("Workflow task", "HIGH", projectId, teamId));
        assertThat(response.statusCode()).isEqualTo(201);
        String location = response.headers().firstValue("location").orElseThrow();
        UUID taskId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        return new TaskWorkflow(taskId, teamId, leaderId, employeeOneId, employeeTwoId, projectManagerId);
    }

    private TaskWorkflow createAssignedTask() throws Exception {
        TaskWorkflow workflow = createDraftTask();
        assertThat(approve(workflow).statusCode()).isEqualTo(200);
        HttpResponse<String> assignment = post(leaderToken(workflow),
                "/api/v1/tasks/" + workflow.taskId() + "/assign",
                assignmentRequest(workflow.employeeTwoId(), 1));
        assertThat(assignment.statusCode()).isEqualTo(200);
        return workflow;
    }

        private QueryFixture insertQueryFixture() {
        UUID teamOneId = createTeam();
        UUID teamTwoId = createTeam();
        UUID projectOneId = UUID.randomUUID();
        UUID projectTwoId = UUID.randomUUID();
        UUID employeeOneId = UUID.randomUUID();
        UUID employeeTwoId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID otherCreatorId = UUID.randomUUID();
        UUID employeeOneTaskId = UUID.randomUUID();
        UUID employeeTwoTaskId = UUID.randomUUID();
        UUID employeeOneOtherTeamTaskId = UUID.randomUUID();
        UUID otherCreatorTaskId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?), (?, ?, ?)",
            projectOneId, teamOneId, "project-one-" + projectOneId,
            projectTwoId, teamTwoId, "project-two-" + projectTwoId);
        jdbcTemplate.update("INSERT INTO team_memberships (team_id, user_id) VALUES "
                + "(?, ?), (?, ?), (?, ?), (?, ?)",
            teamOneId, employeeOneId, teamOneId, employeeTwoId, teamOneId, leaderId,
            teamTwoId, employeeOneId);
        jdbcTemplate.update("INSERT INTO team_leaderships (team_id, user_id) VALUES (?, ?)", teamOneId, leaderId);

        insertQueryTask(employeeOneTaskId, "A assigned", "IN_PROGRESS", "HIGH", employeeOneId,
            teamOneId, projectOneId, creatorId, Instant.parse("2026-01-01T00:00:00Z"));
        insertQueryTask(employeeTwoTaskId, "B assigned", "COMPLETED", "MEDIUM", employeeTwoId,
            teamOneId, projectOneId, creatorId, Instant.parse("2026-01-02T00:00:00Z"));
        insertQueryTask(employeeOneOtherTeamTaskId, "A other team", "DRAFT", "LOW", employeeOneId,
            teamTwoId, projectTwoId, creatorId, Instant.parse("2026-01-03T00:00:00Z"));
        insertQueryTask(UUID.randomUUID(), "Unassigned", "DRAFT", "URGENT", null,
            teamOneId, projectOneId, creatorId, Instant.parse("2026-01-04T00:00:00Z"));
        insertQueryTask(otherCreatorTaskId, "Other creator", "DRAFT", "LOW", null,
            teamTwoId, projectTwoId, otherCreatorId, Instant.parse("2026-01-05T00:00:00Z"));
        jdbcTemplate.update("INSERT INTO task_audit_log (id, task_id, actor_id, action, new_status, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), employeeOneTaskId, leaderId,
            "CREATED", "IN_PROGRESS", Timestamp.from(Instant.parse("2026-01-01T01:00:00Z")));
        return new QueryFixture(teamOneId, teamTwoId, projectOneId, projectTwoId, employeeOneId,
            employeeTwoId, leaderId, creatorId, otherCreatorId, employeeOneTaskId, employeeTwoTaskId,
            employeeOneOtherTeamTaskId);
        }

        private void insertQueryTask(UUID taskId, String title, String status, String priority, UUID assignedTo,
            UUID teamId, UUID projectId, UUID createdBy, Instant createdAt) {
        jdbcTemplate.update("INSERT INTO tasks (id, title, status, priority, assigned_to, team_id, project_id, "
                + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            taskId, title, status, priority, assignedTo, teamId, projectId, createdBy,
            Timestamp.from(createdAt), Timestamp.from(createdAt));
        }

        private void assertQueryHasOne(String jwt, String path) throws Exception {
        HttpResponse<String> response = get(jwt, path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"totalElements\":1");
        }

        private record QueryFixture(UUID teamOneId, UUID teamTwoId, UUID projectOneId, UUID projectTwoId,
            UUID employeeOneId, UUID employeeTwoId, UUID leaderId, UUID creatorId, UUID otherCreatorId,
            UUID employeeOneTaskId, UUID employeeTwoTaskId, UUID employeeOneOtherTeamTaskId) {
        }

    private HttpResponse<String> approve(TaskWorkflow workflow) throws Exception {
        return post(leaderToken(workflow), "/api/v1/tasks/" + workflow.taskId() + "/approve",
                "{\"version\":0}");
    }

    private String leaderToken(TaskWorkflow workflow) {
        return token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("TEAM_LEADER"), workflow.leaderId().toString());
    }

    private HttpResponse<String> assignAfterBarrier(TaskWorkflow workflow, UUID employeeId, String leaderToken,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent assignment start was not released");
        }
        return post(leaderToken, "/api/v1/tasks/" + workflow.taskId() + "/assign",
                assignmentRequest(employeeId, 1));
    }

    private String assignmentRequest(UUID employeeId, long version) {
        return "{\"employeeId\":\"" + employeeId + "\",\"version\":" + version + "}";
    }

    private String statusRequest(String status, long version) {
        return "{\"status\":\"" + status + "\",\"version\":" + version + "}";
    }

    private String employeeToken(UUID employeeId) {
        return token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                List.of("EMPLOYEE"), employeeId.toString());
    }

    private HttpResponse<String> patch(String token, UUID taskId, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/tasks/" + taskId + "/status"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

        private record TaskWorkflow(UUID taskId, UUID teamId, UUID leaderId, UUID employeeOneId,
            UUID employeeTwoId, UUID projectManagerId) {
    }

    private UUID createProject() {
        UUID teamId = createTeam();
        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), teamId, "project-" + UUID.randomUUID());
        return teamId;
    }

    private UUID createTeam() {
        UUID teamId = UUID.randomUUID();
        insertTeam(teamId);
        return teamId;
    }

    private String taskRequest(String title, String priority, UUID projectId, UUID teamId) {
        return """
                {"title":"%s","description":"A task description","priority":"%s",
                 "projectId":"%s","teamId":"%s","dueDate":"2030-04-05"}
                """.formatted(title, priority, projectId, teamId);
    }

    private void assertProblemDetail(HttpResponse<String> response, String title) {
        assertProblemDetail(response, title, "/api/v1/tasks");
        }

        private void assertProblemDetail(HttpResponse<String> response, String title, String instance) {
        assertThat(response.headers().firstValue("content-type").orElseThrow())
                .contains("application/problem+json");
        assertThat(response.body()).contains("\"type\"", "\"title\":\"" + title + "\"",
            "\"status\":" + response.statusCode(), "\"instance\":\"" + instance + "\"");
    }

    private String endpoint() {
        return "http://127.0.0.1:" + port + "/api/v1/whoami";
    }

    private static String startJwkServer() {
        try {
            jwkServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwkServer.createContext("/oauth2/jwks", exchange -> {
                byte[] body = PUBLIC_JWK_SET.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            jwkServer.start();
            return "http://127.0.0.1:" + jwkServer.getAddress().getPort() + "/oauth2/jwks";
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start test JWKS endpoint", exception);
        }
    }

    private static String token(String issuer, String audience, Instant expiresAt) {
        return token(issuer, audience, expiresAt, List.of());
    }

    private static String token(String issuer, String audience, Instant expiresAt, List<String> roles) {
        return token(issuer, audience, expiresAt, roles, "integration-user");
        }

        private static String token(String issuer, String audience, Instant expiresAt,
            List<String> roles, String subject) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(privateJwk())));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
            .issuedAt(expiresAt.isBefore(now) ? expiresAt.minusSeconds(120) : now.minusSeconds(1))
                .expiresAt(expiresAt)
                .audience(List.of(audience))
                .claim("scope", "task.read")
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("integration-key").build(), claims)).getTokenValue();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate test signing key", exception);
        }
    }

    private static RSAKey privateJwk() {
        return new RSAKey.Builder((RSAPublicKey) SIGNING_KEYS.getPublic())
                .privateKey((RSAPrivateKey) SIGNING_KEYS.getPrivate())
                .keyID("integration-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private static String publicJwkSet() {
        try {
            return new JWKSet(privateJwk().toPublicJWK()).toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize test public key", exception);
        }
    }
}
