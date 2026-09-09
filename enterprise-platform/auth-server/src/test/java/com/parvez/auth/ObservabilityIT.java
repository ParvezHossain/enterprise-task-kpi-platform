package com.parvez.auth;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import com.parvez.auth.service.RegistrationRequest;
import com.parvez.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT extends PostgresRepositoryTestSupport {
    @LocalServerPort int port;
    @Autowired UserService users;
    @Autowired ObjectMapper json;

    @Test
    void actuatorRequiresSessionAndReturnsRealMetrics(CapturedOutput output) throws Exception {
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5)).build();
        for (String path : new String[]{"/actuator/health", "/actuator/info"}) {
            assertThat(get(browser, path).statusCode()).isEqualTo(200);
        }
        for (String path : new String[]{"/actuator", "/actuator/metrics", "/actuator/metrics/jvm.memory.used",
                "/actuator/prometheus", "/actuator/env"}) {
            var denied = get(browser, path, "text/html");
            assertThat(denied.statusCode()).as(path).isEqualTo(401);
            assertThat(denied.headers().firstValue("content-type").orElseThrow())
                    .startsWith("application/problem+json");
        }
        String email = "metrics-" + UUID.randomUUID() + "@example.test";
        String password = "Metrics-password-canary-123";
        users.register(new RegistrationRequest(email, password));
        var page = get(browser, "/login");
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(csrf.find()).isTrue();
        String form = "username=" + encode(email) + "&password=" + encode(password) + "&_csrf=" + encode(csrf.group(1));
        var login = browser.send(HttpRequest.newBuilder(uri("/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);
        var metrics = get(browser, "/actuator/metrics");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("jvm.memory.used", "http.server.requests");
        assertThat(get(browser, "/actuator/metrics/jvm.memory.used").statusCode()).isEqualTo(200);
        var scrape = get(browser, "/actuator/prometheus?token=query-observability-canary");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("jvm_memory_used_bytes", "http_server_requests_seconds");
        assertThat(get(browser, "/actuator/env").statusCode()).isEqualTo(404);

        String requestId = scrape.headers().firstValue("X-Request-ID").orElseThrow();
        assertThat(requestId).matches("[0-9a-f-]{36}");
        // Completion is logged just after the response is written.
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!output.getAll().contains("\"requestId\":\"" + requestId + "\"") && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        var event = output.getAll().lines().filter(line -> line.startsWith("{") && line.contains(requestId))
                .map(json::readTree).filter(node -> node.path("message").asText().equals("HTTP request completed"))
                .findFirst().orElseThrow();
        assertThat(event.path("traceId").asText()).matches("[0-9a-f]{32}");
        assertThat(event.path("status").asInt()).isEqualTo(200);
        assertThat(event.path("durationMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(output.getAll()).doesNotContain(password, "query-observability-canary", "untrusted-id-canary");
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return get(client, path, "*/*");
    }
    private HttpResponse<String> get(HttpClient client, String path, String accept) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Accept", accept).header("X-Request-ID", "untrusted-id-canary")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
