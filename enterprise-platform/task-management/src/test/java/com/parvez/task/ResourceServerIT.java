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
        void migrationCreatesRequiredTaskIndexesAndForeignKeys() {
        var taskIndexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND tablename = 'tasks'",
            String.class);
            var schemaIndexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() "
                    + "AND tablename IN ('team_memberships', 'task_audit_log')",
                String.class);
            var schemaForeignKeys = jdbcTemplate.queryForList(
            "SELECT conname FROM pg_constraint WHERE connamespace = current_schema()::regnamespace AND contype = 'f'",
            String.class);

        assertThat(taskIndexes).contains(
            "idx_tasks_assigned_to", "idx_tasks_team_id", "idx_tasks_status",
            "idx_tasks_created_at", "idx_tasks_due_date", "idx_tasks_project_id");
        assertThat(schemaIndexes).contains("idx_team_memberships_user_id", "idx_task_audit_log_task_occurred_at");
        assertThat(schemaForeignKeys).contains(
            "fk_team_memberships_team", "fk_projects_team", "fk_tasks_team",
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
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/tasks"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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
        assertThat(response.headers().firstValue("content-type").orElseThrow())
                .contains("application/problem+json");
        assertThat(response.body()).contains("\"type\"", "\"title\":\"" + title + "\"",
                "\"status\":" + response.statusCode(), "\"instance\":\"/api/v1/tasks\"");
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
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(privateJwk())));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("integration-user")
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