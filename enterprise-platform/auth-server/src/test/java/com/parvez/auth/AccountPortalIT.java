package com.parvez.auth;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.parvez.auth.service.RegistrationRequest;
import com.parvez.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// A separate context must use this class's live container, rather than a cached context
// from another subclass whose class-scoped PostgreSQL container has already stopped.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.application.name=auth-account-portal-it")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AccountPortalIT extends PostgresRepositoryTestSupport {
    @LocalServerPort int port;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void customLoginAndLogoutPagesContainSessionCsrfAndNeverEchoQueryValues() throws Exception {
        var browser = browser();
        var login = get(browser, "/login?error=%3Cscript%3Elogin-query-canary%3C/script%3E", "text/html");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("Welcome back.", "action=\"/login\"", "autocomplete=\"current-password\"")
                .doesNotContain("login-query-canary", "{{", "value=\"password");
        assertThat(login.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        assertThat(login.headers().firstValue("content-security-policy").orElseThrow()).contains("default-src 'self'");
        assertThat(Pattern.compile("name=\"_csrf\"[^>]*value=\"[^\"]+\"").matcher(login.body()).find()).isTrue();
        assertThat(Pattern.compile("id=\"login-error\"[^>]*hidden").matcher(login.body()).find()).isFalse();
        var signedOut = get(browser, "/login?logout=logout-query-canary", "text/html");
        assertThat(signedOut.body()).contains("You have signed out of Auth.").doesNotContain("logout-query-canary");
        assertThat(Pattern.compile("id=\"logout-notice\"[^>]*hidden").matcher(signedOut.body()).find()).isFalse();
        var confirmation = get(browser, "/logout", "text/html");
        assertThat(confirmation.statusCode()).isEqualTo(200);
        assertThat(confirmation.body()).contains("Sign out of Auth?", "action=\"/logout\"", "method=\"post\"");
        assertThat(Pattern.compile("name=\"_csrf\"[^>]*value=\"[^\"]+\"").matcher(confirmation.body()).find()).isTrue();
        for (String path : new String[]{"/account-assets/login.css", "/account-assets/login.js"})
            assertThat(get(browser, path).statusCode()).isEqualTo(200);
    }

    @Test
    void browserCredentialFailuresReturnTheSameStyledErrorAndPreserveSavedRequest(CapturedOutput output) throws Exception {
        String password = "Browser-login-" + UUID.randomUUID();
        var active = users.register(new RegistrationRequest("browser-" + UUID.randomUUID() + "@example.org", password));
        var disabled = users.register(new RegistrationRequest("disabled-" + UUID.randomUUID() + "@example.org", password));
        jdbc.update("UPDATE users SET enabled=false WHERE id=?", disabled.id());
        var browser = browser();
        get(browser, "/account", "text/html");
        for (String email : new String[]{active.email(), disabled.email(), "missing-" + UUID.randomUUID() + "@example.org"}) {
            var page = get(browser, "/login", "text/html");
            var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
            assertThat(token.find()).isTrue();
            var failure = post(browser, "/login", Map.of("username", email, "password", "wrong-browser-password",
                    "_csrf", token.group(1)), "text/html");
            assertThat(failure.statusCode()).isEqualTo(302);
            var location = URI.create("http://127.0.0.1:" + port)
                    .resolve(failure.headers().firstValue("location").orElseThrow());
            assertThat(location).isEqualTo(URI.create("http://127.0.0.1:" + port + "/login?error"));
            assertThat(get(browser, "/login?error", "text/html").body()).doesNotContain(email, password, "wrong-browser-password");
        }
        var success = login(browser, active.email(), password);
        assertThat(success.statusCode()).isEqualTo(302);
        assertThat(URI.create(success.headers().firstValue("location").orElseThrow()).getPath()).isEqualTo("/account");
        assertThat(output.getAll()).doesNotContain(password, "wrong-browser-password");
    }

    @Test
    void rootAndStaticAssetsAreUsableButIdentityAndHistoryRequireSession() throws Exception {
        var browser = browser();
        var root = get(browser, "/", "text/html");
        assertThat(root.statusCode()).isEqualTo(302);
        assertThat(root.headers().firstValue("Location")).hasValue("/account");
        assertThat(get(browser, "/account", "text/html").statusCode()).isEqualTo(302);
        for (String path : new String[]{"/api/v1/account", "/api/v1/account/activity"}) {
            var response = get(browser, path);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith("application/problem+json");
        }
        for (String path : new String[]{"/account-assets/account.css", "/account-assets/account.js"})
            assertThat(get(browser, path).statusCode()).isEqualTo(200);
        assertThat(get(browser, "/account-assets/missing.js").statusCode()).isEqualTo(401);
    }

    @Test
    void accountShowsOwnActivityAndCsrfProtectedLogoutEndsTheSession(CapturedOutput output) throws Exception {
        String email = "portal-" + UUID.randomUUID() + "@example.org";
        String password = "Portal-password-" + UUID.randomUUID();
        var user = users.register(new RegistrationRequest(email, password));
        var browser = browser();
        // Visiting the previously forbidden root must no longer save a forbidden post-login destination.
        get(browser, "/", "text/html");
        get(browser, "/account", "text/html");
        var login = login(browser, email, password);
        assertThat(login.statusCode()).isEqualTo(302);
        // Spring Security appends its saved-request continuation query parameter.
        assertThat(URI.create(login.headers().firstValue("location").orElseThrow()).getPath()).isEqualTo("/account");
        var page = get(browser, "/account", "text/html");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Recent activity", "Sign out of Auth", "Single sign-on", "action=\"/logout\"", "method=\"post\"");
        assertThat(page.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
        assertThat(page.headers().firstValue("content-security-policy").orElseThrow()).contains("default-src 'self'");
        JsonNode account = json.readTree(get(browser, "/api/v1/account").body());
        assertThat(account.path("subject").asText()).isEqualTo(user.id().toString());
        assertThat(account.path("email").asText()).isEqualTo(email);
        assertThat(account.path("roles").get(0).asText()).isEqualTo("EMPLOYEE");
        assertThat(account.path("taskUrl").asText()).isEqualTo("http://127.0.0.1:8080/");
        assertThat(account.path("kpiUrl").asText()).isEqualTo("http://127.0.0.1:8081/");
        assertThat(account.path("csrfToken").asText()).isNotBlank();
        assertThat(account.toString()).doesNotContain(password, "passwordHash", "access_token", "refresh_token", "JSESSIONID");
        var events = json.readTree(get(browser, "/api/v1/account/activity").body()).path("content");
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.get(0).path("event").asText()).isEqualTo("LOGIN_SUCCEEDED");
        assertThat(events.get(0).size()).isEqualTo(3); // id/event/time only, not identities or credentials.

        assertThat(post(browser, "/logout", Map.of()).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authentication_activity WHERE user_id=? AND event='AUTH_LOGOUT'", Long.class, user.id())).isZero();
        assertThat(post(browser, "/logout", Map.of(account.path("csrfParameterName").asText(), account.path("csrfToken").asText())).statusCode()).isEqualTo(302);
        assertThat(get(browser, "/api/v1/account").statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authentication_activity WHERE user_id=? AND event='AUTH_LOGOUT'", Long.class, user.id())).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(password);
        login(browser, email, password);
        assertThat(json.readTree(get(browser, "/api/v1/account/activity").body()).path("content").size()).isEqualTo(3);
    }

    @Test
    void historyIsBoundedOwnedAndRejectsInvalidQueriesAndDisabledUsers() throws Exception {
        String password = "Activity-password-" + UUID.randomUUID();
        var own = users.register(new RegistrationRequest("own-" + UUID.randomUUID() + "@example.org", password));
        var other = users.register(new RegistrationRequest("other-" + UUID.randomUUID() + "@example.org", password));
        for (int n = 0; n < 55; n++)
            jdbc.update("INSERT INTO authentication_activity(id,user_id,event) VALUES(?,?,'LOGIN_FAILED')", UUID.randomUUID(), own.id());
        UUID otherEvent = UUID.randomUUID();
        jdbc.update("INSERT INTO authentication_activity(id,user_id,event) VALUES(?,?,'AUTH_LOGOUT')", otherEvent, other.id());
        var browser = browser();
        login(browser, own.email(), password);
        var first = json.readTree(get(browser, "/api/v1/account/activity?size=999&userId=" + other.id()).body());
        assertThat(first.path("size").asInt()).isEqualTo(50);
        assertThat(first.path("content").size()).isEqualTo(50);
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        assertThat(first.toString()).doesNotContain(otherEvent.toString(), other.email());
        var second = json.readTree(get(browser, "/api/v1/account/activity?page=1&size=999").body());
        assertThat(second.path("content").size()).isEqualTo(6);
        assertThat(second.path("hasNext").asBoolean()).isFalse();
        for (String query : new String[]{"page=-1", "page=100001", "size=0", "size=-1", "page=bad"})
            assertThat(get(browser, "/api/v1/account/activity?" + query).statusCode()).as(query).isEqualTo(400);
        jdbc.update("UPDATE users SET enabled=false WHERE id=?", own.id());
        assertThat(get(browser, "/api/v1/account").statusCode()).isEqualTo(401);
        assertThat(get(browser, "/api/v1/account/activity").statusCode()).isEqualTo(401);
    }

    @Test
    void knownAccountFailuresAreRecordedWithoutChangingPublicFailureResponse() throws Exception {
        String password = "Failed-password-" + UUID.randomUUID();
        var user = users.register(new RegistrationRequest("failure-" + UUID.randomUUID() + "@example.org", password));
        var browser = browser();
        var failed = login(browser, " " + user.email().toUpperCase(java.util.Locale.ROOT) + " ", "incorrect-password-123");
        assertThat(failed.statusCode()).isEqualTo(401);
        assertThat(failed.body()).doesNotContain(user.email(), password, "incorrect-password-123");
        var unknown = login(browser, "unknown-" + UUID.randomUUID() + "@example.org", "incorrect-password-123");
        assertThat(json.readTree(unknown.body())).isEqualTo(json.readTree(failed.body()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authentication_activity WHERE user_id=? AND event='LOGIN_FAILED'", Long.class, user.id())).isEqualTo(1);
        login(browser, user.email(), password);
        assertThat(json.readTree(get(browser, "/api/v1/account/activity").body()).path("content").size()).isEqualTo(2);
    }

    private HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return get(browser, path, "application/json");
    }

    private HttpResponse<String> get(HttpClient browser, String path, String accept) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept", accept).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(HttpClient browser, String email, String password) throws Exception {
        var page = get(browser, "/login", "text/html");
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(token.find()).isTrue();
        return post(browser, "/login", Map.of("username", email, "password", password, "_csrf", token.group(1)));
    }

    private HttpResponse<String> post(HttpClient browser, String path, Map<String, String> fields) throws Exception {
        return post(browser, path, fields, "application/json");
    }

    private HttpResponse<String> post(HttpClient browser, String path, Map<String, String> fields, String accept) throws Exception {
        String body = fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        return browser.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded").header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
