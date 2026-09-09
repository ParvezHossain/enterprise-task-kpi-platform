package com.parvez.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import jakarta.validation.Valid;
import com.parvez.auth.service.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ProblemDetailsIT.Fixture.class)
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsIT extends PostgresRepositoryTestSupport {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void malformedBodiesHaveExactProblemShapeAndNeverEchoOrLogValues(CapturedOutput output) throws Exception {
        for (String body : new String[]{"{\"password\":\"malformed-password-canary\",", "", "[]"}) {
            var response = send("POST", "/test/problems/body?token=query-token-canary", body, "application/json");
            assertProblem(response, 400, "Bad Request", "The request is invalid.", "/test/problems/body");
            assertThat(response.body()).doesNotContain("canary", "exception", "trace");
        }
        assertThat(output.getAll()).doesNotContain("malformed-password-canary", "query-token-canary");
    }

    @Test
    void beanValidationHasExactProblemShapeWithoutRejectedValues(CapturedOutput output) throws Exception {
        var response = send("POST", "/test/problems/body", """
                {"email":"invalid-email-canary","password":"short"}
                """, "application/json");
        assertProblem(response, 400, "Bad Request", "The request is invalid.", "/test/problems/body");
        assertThat(output.getAll()).doesNotContain("invalid-email-canary");
    }

    @Test
    void unexpectedExceptionsAndFrameworkErrorsUseTheSameContract(CapturedOutput output) throws Exception {
        assertProblem(send("GET", "/test/problems/failure", "", "application/json"),
                500, "Internal Server Error", "An unexpected server error occurred.", "/test/problems/failure");
        assertProblem(send("POST", "/test/problems/body", "secret", "text/plain"),
                415, "Unsupported Media Type", "The request content type is not supported.", "/test/problems/body");
        assertProblem(send("GET", "/test/problems/missing", "", "application/json"),
                404, "Not Found", "The requested resource was not found.", "/test/problems/missing");
        assertProblem(send("POST", "/login", "password=csrf-password-canary", "application/x-www-form-urlencoded"),
                403, "Forbidden", "Access to this resource is denied.", "/login");
        assertThat(output.getAll()).doesNotContain("exception-token-canary", "csrf-password-canary");
    }

    @Test
    void bearerErrorsKeepTheChallengeWithoutDecoderDetails(CapturedOutput output) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/userinfo"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer invalid-token-canary")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("www-authenticate").orElseThrow()).isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(json.readTree(response.body())).isEqualTo(json.readTree("""
                {"type":"about:blank","title":"Unauthorized","status":401,
                 "detail":"Authentication failed or is required.","instance":"/userinfo","error":"invalid_token"}
                """));
        assertThat(output.getAll()).doesNotContain("invalid-token-canary");
    }

    @Test
    void headErrorsPreserveStatusAndMediaTypeWithoutABody() throws Exception {
        var response = send("HEAD", "/test/problems/missing", "", "application/json");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.body()).isEmpty();
    }

    private void assertProblem(HttpResponse<String> response, int status, String title, String detail, String instance) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("cache-control").orElseThrow()).isEqualTo("no-store");
        assertThat(json.readTree(response.body())).isEqualTo(json.valueToTree(java.util.Map.of(
                "type", "about:blank", "title", title, "status", status, "detail", detail, "instance", instance)));
    }

    private HttpResponse<String> send(String method, String path, String body, String type) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", type).header("Accept", "text/html")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    // Exercise the production MVC advice over real HTTP without introducing a public
    // registration endpoint. This controller and its security chain exist only in this test.
    @TestConfiguration(proxyBeanMethods = false)
    static class Fixture {
        @Bean BodyController bodyController() { return new BodyController(); }
        @Bean @Order(0)
        SecurityFilterChain fixtureChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/test/problems/**")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable()).build();
        }
    }

    @RestController
    static class BodyController {
        @PostMapping("/test/problems/body")
        String body(@Valid @RequestBody RegistrationRequest request) { return "accepted"; }
        @GetMapping("/test/problems/failure")
        String failure() { throw new IllegalStateException("exception-token-canary"); }
    }
}
