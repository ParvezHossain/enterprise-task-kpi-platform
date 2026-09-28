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
        String requestBody = taskRequest("Review API", "HIGH", projectId, teamId);

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("PROJECT_MANAGER")), requestBody);

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

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
            List.of("ADMIN")), taskRequest("Admin task", "MEDIUM", projectId, teamId));

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
            "idx_tasks_created_at", "idx_tasks_due_date", "idx_tasks_project_id");
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
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint())).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String token, String body) throws Exception {
        return post(token, "/api/v1/tasks", body);
    }

    private HttpResponse<String> post(String token, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
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
        jdbcTemplate.update("INSERT INTO projects (id, team_id, name) VALUES (?, ?, ?)",
                projectId, teamId, "project-" + projectId);
        jdbcTemplate.update("INSERT INTO team_memberships (team_id, user_id) VALUES (?, ?), (?, ?), (?, ?)",
                teamId, leaderId, teamId, employeeOneId, teamId, employeeTwoId);
        jdbcTemplate.update("INSERT INTO team_leaderships (team_id, user_id) VALUES (?, ?)", teamId, leaderId);

        HttpResponse<String> response = post(token(ISSUER, "task-management", Instant.now().plusSeconds(60),
                List.of("PROJECT_MANAGER")), taskRequest("Workflow task", "HIGH", projectId, teamId));
        assertThat(response.statusCode()).isEqualTo(201);
        String location = response.headers().firstValue("location").orElseThrow();
        UUID taskId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        return new TaskWorkflow(taskId, teamId, leaderId, employeeOneId, employeeTwoId);
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

    private record TaskWorkflow(UUID taskId, UUID teamId, UUID leaderId, UUID employeeOneId, UUID employeeTwoId) {
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