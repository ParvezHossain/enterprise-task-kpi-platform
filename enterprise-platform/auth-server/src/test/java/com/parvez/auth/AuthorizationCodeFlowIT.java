package com.parvez.auth;

import java.net.CookieManager;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.nimbusds.jose.jwk.JWKSet;
import com.parvez.auth.service.RegistrationRequest;
import com.parvez.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(OutputCaptureExtension.class)
class AuthorizationCodeFlowIT {
    static final int PORT = freePort();
    static final String ISSUER = "http://127.0.0.1:" + PORT;
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestAuthMaterial.register(registry, ISSUER);
        registry.add("server.port", () -> PORT);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.runtimeRole", POSTGRES::getUsername);
    }

    @ParameterizedTest
    @ValueSource(strings = {"task-management-ui", "kpi-ui"})
    void completeOidcPkceAndRefreshFlow(String clientId, CapturedOutput output) throws Exception {
        var browser = browser();
        String password = "Flow-password-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.org";
        var user = users.register(new RegistrationRequest(email, password));
        jdbc.update("INSERT INTO permissions(id,name) VALUES (?, 'flow.read') ON CONFLICT(name) DO NOTHING", UUID.randomUUID());
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r,permissions p WHERE r.name='EMPLOYEE' AND p.name='flow.read'
                ON CONFLICT DO NOTHING
                """);
        var discovery = json.readTree(get(browser, "/.well-known/openid-configuration").body());
        assertThat(discovery.path("issuer").asText()).isEqualTo(ISSUER);
        assertThat(discovery.path("authorization_endpoint").asText()).isEqualTo(ISSUER + "/oauth2/authorize");
        assertThat(discovery.path("token_endpoint").asText()).isEqualTo(ISSUER + "/oauth2/token");
        assertThat(discovery.path("jwks_uri").asText()).isEqualTo(ISSUER + "/oauth2/jwks");
        var jwks = JWKSet.parse(get(browser, "/oauth2/jwks").body());
        assertThat(jwks.getKeys()).hasSize(1).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
        var decoder = NimbusJwtDecoder.withPublicKey(jwks.getKeys().getFirst().toRSAKey().toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));

        String verifier = verifier();
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        String authorize = authorization(clientId, verifier, state, nonce);
        var unauthenticated = get(browser, authorize);
        assertThat(unauthenticated.statusCode()).isEqualTo(302);
        assertThat(unauthenticated.headers().firstValue("location")).hasValue(ISSUER + "/login");
        login(browser, email, password);
        var authorization = get(browser, authorize);
        String code = code(authorization, state, clientId);
        var tokens = exchange(browser, clientId, code, verifier);
        assertThat(tokens.statusCode()).isEqualTo(200);
        var token = json.readTree(tokens.body());
        assertThat(token.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(token.path("expires_in").asInt()).isBetween(1, 300);
        Jwt access = decoder.decode(token.path("access_token").asText());
        Jwt id = decoder.decode(token.path("id_token").asText());
        assertThat(access.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(access.getHeaders().get("kid")).isEqualTo("integration-test-key");
        assertThat(access.getSubject()).isEqualTo(user.id().toString());
        assertThat(access.getAudience()).containsExactly(clientId.equals("kpi-ui") ? "kpi-service" : "task-management");
        assertThat(access.getClaimAsStringList("roles")).containsExactly("EMPLOYEE");
        assertThat(access.getClaimAsStringList("authorities")).contains("ROLE_EMPLOYEE", "flow.read");
        assertThat(id.getAudience()).containsExactly(clientId);
        assertThat(id.getSubject()).isEqualTo(access.getSubject());
        assertThat(id.getClaimAsString("nonce")).isEqualTo(nonce);
        assertThat(id.getClaimAsString("email")).isEqualTo(email);
        assertThat(id.getClaims()).doesNotContainKeys("email_verified", "password", "password_hash");
        var userInfo = send(browser, HttpRequest.newBuilder(URI.create(ISSUER + "/userinfo"))
                .header("Authorization", "Bearer " + token.path("access_token").asText()).GET());
        assertThat(userInfo.statusCode()).isEqualTo(200);
        assertThat(json.readTree(userInfo.body()).path("sub").asText()).isEqualTo(user.id().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?", Long.class,
                user.id().toString())).isEqualTo(1L);
        String attributes = jdbc.queryForObject("SELECT attributes FROM oauth2_authorization WHERE principal_name = ?", String.class,
                user.id().toString());
        assertThat(attributes).doesNotContain(password, "$argon2id$");

        String oldRefresh = token.path("refresh_token").asText();
        assertThat(oldRefresh).isNotBlank();
        var refreshed = refresh(browser, clientId, oldRefresh);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        var rotated = json.readTree(refreshed.body());
        assertThat(rotated.path("refresh_token").asText()).isNotBlank().isNotEqualTo(oldRefresh);
        assertThat(decoder.decode(rotated.path("access_token").asText()).getSubject()).isEqualTo(user.id().toString());
        assertError(refresh(browser, clientId, oldRefresh), "invalid_grant");
        assertError(exchange(browser, clientId, code, verifier), "invalid_grant");
        var secrets = new ArrayList<>(List.of(password, TestAuthMaterial.TASK_SECRET, TestAuthMaterial.KPI_SECRET, code, verifier));
        for (var value : List.of(token, rotated)) {
            for (String field : List.of("access_token", "id_token", "refresh_token")) {
                if (value.has(field)) secrets.add(value.path(field).asText());
            }
        }
        assertThat(output.getAll()).doesNotContain(secrets.toArray(String[]::new));
    }

    @Test
    void invalidPkceRedirectAndClientCredentialsAreRejected() throws Exception {
        var browser = browser();
        String email = UUID.randomUUID() + "@example.org";
        users.register(new RegistrationRequest(email, "Rejection-password-123"));
        login(browser, email, "Rejection-password-123");
        String verifier = verifier();
        String state = UUID.randomUUID().toString();
        String request = authorization("task-management-ui", verifier, state, "nonce");
        var missing = get(browser, request.replaceAll("&code_challenge=[^&]+&code_challenge_method=S256", ""));
        assertThat(missing.statusCode()).isEqualTo(302);
        assertThat(query(URI.create(missing.headers().firstValue("location").orElseThrow())).get("error")).isEqualTo("invalid_request");
        var plain = get(browser, request.replace("code_challenge_method=S256", "code_challenge_method=plain"));
        assertThat(plain.statusCode()).isEqualTo(302);
        assertThat(query(URI.create(plain.headers().firstValue("location").orElseThrow())).get("error")).isEqualTo("invalid_request");
        var badRedirect = get(browser, request.replace(encode(redirect("task-management-ui")), encode("https://attacker.invalid/callback")));
        assertThat(badRedirect.statusCode()).isEqualTo(400);
        assertThat(badRedirect.headers().firstValue("location")).isEmpty();
        String code = code(get(browser, request), state, "task-management-ui");
        assertError(exchange(browser, "task-management-ui", code, verifier()), "invalid_grant");
        var badClient = post(browser, "/oauth2/token", Map.of("grant_type", "refresh_token", "refresh_token", "invalid"),
                "task-management-ui", "incorrect-secret");
        assertThat(badClient.statusCode()).isEqualTo(401);
        assertError(badClient, "invalid_client");
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        var browser = browser();
        String email = UUID.randomUUID() + "@example.org";
        var user = users.register(new RegistrationRequest(email, "Disabled-password-123"));
        login(browser, email, "Disabled-password-123");
        String verifier = verifier();
        String state = UUID.randomUUID().toString();
        var token = json.readTree(exchange(browser, "kpi-ui",
                code(get(browser, authorization("kpi-ui", verifier, state, "nonce")), state, "kpi-ui"), verifier).body());
        jdbc.update("UPDATE users SET enabled = false WHERE id = ?", user.id());
        assertError(refresh(browser, "kpi-ui", token.path("refresh_token").asText()), "invalid_grant");
    }

    private String authorization(String clientId, String verifier, String state, String nonce) throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        return "/oauth2/authorize?" + form(new LinkedHashMap<>(Map.of(
                "response_type", "code", "client_id", clientId, "redirect_uri", redirect(clientId),
                "scope", "openid profile email " + (clientId.equals("kpi-ui") ? "kpi.read" : "task.read"),
                "state", state, "nonce", nonce))) + "&code_challenge=" + challenge + "&code_challenge_method=S256";
    }

    private void login(HttpClient browser, String email, String password) throws Exception {
        var page = get(browser, "/login");
        assertThat(page.statusCode()).isEqualTo(200);
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        var result = post(browser, "/login", Map.of("username", email, "password", password, "_csrf", csrf.group(1)), null, null);
        assertThat(result.statusCode()).isEqualTo(302);
        assertThat(result.headers().firstValue("location").orElseThrow()).doesNotContain("error");
    }

    private String code(HttpResponse<String> response, String state, String clientId) {
        assertThat(response.statusCode()).isEqualTo(302);
        var location = URI.create(response.headers().firstValue("location").orElseThrow());
        assertThat(location.toString()).startsWith(redirect(clientId) + "?");
        assertThat(query(location)).containsEntry("state", state).containsKey("code").doesNotContainKey("error");
        return query(location).get("code");
    }

    private HttpResponse<String> exchange(HttpClient browser, String clientId, String code, String verifier) throws Exception {
        return post(browser, "/oauth2/token", Map.of("grant_type", "authorization_code", "code", code,
                "redirect_uri", redirect(clientId), "code_verifier", verifier), clientId, secret(clientId));
    }

    private HttpResponse<String> refresh(HttpClient browser, String clientId, String token) throws Exception {
        return post(browser, "/oauth2/token", Map.of("grant_type", "refresh_token", "refresh_token", token), clientId, secret(clientId));
    }

    private void assertError(HttpResponse<String> response, String error) throws Exception {
        assertThat(response.statusCode()).isBetween(400, 401);
        assertThat(json.readTree(response.body()).path("error").asText()).isEqualTo(error);
    }

    private HttpResponse<String> post(HttpClient browser, String path, Map<String, String> fields, String id, String secret) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(ISSUER + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields)));
        if (id != null) builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        return send(browser, builder);
    }

    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return send(browser, HttpRequest.newBuilder(URI.create(ISSUER + path)).header("Accept", "text/html").GET());
    }

    private HttpResponse<String> send(HttpClient browser, HttpRequest.Builder builder) throws Exception {
        return browser.send(builder.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static String secret(String clientId) { return clientId.equals("kpi-ui") ? TestAuthMaterial.KPI_SECRET : TestAuthMaterial.TASK_SECRET; }
    private static String redirect(String clientId) { return "http://127.0.0.1:" + (clientId.equals("kpi-ui") ? "8081" : "8080") + "/login/oauth2/code/" + clientId; }
    private static String verifier() { return UUID.randomUUID().toString() + UUID.randomUUID(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }
    private static Map<String, String> query(URI uri) {
        var result = new LinkedHashMap<String, String>();
        for (String part : uri.getRawQuery().split("&")) {
            var pair = part.split("=", 2);
            result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    private static int freePort() {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
        catch (Exception exception) { throw new IllegalStateException("Cannot allocate test HTTP port"); }
    }
}
