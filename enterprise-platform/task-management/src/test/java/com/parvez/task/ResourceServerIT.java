package com.parvez.task;

import static org.assertj.core.api.Assertions.assertThat;

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

    private HttpResponse<String> get(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint())).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
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
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(privateJwk())));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("integration-user")
            .issuedAt(expiresAt.isBefore(now) ? expiresAt.minusSeconds(120) : now.minusSeconds(1))
                .expiresAt(expiresAt)
                .audience(List.of(audience))
                .claim("scope", "task.read")
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