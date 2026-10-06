package com.parvez.auth;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MachineClientIT extends PostgresRepositoryTestSupport {
    static final String SECRET=UUID.randomUUID().toString();
    @LocalServerPort int port;
    @DynamicPropertySource static void material(DynamicPropertyRegistry properties) {
        TestAuthMaterial.register(properties,"https://auth.example.test");
        properties.add("auth.kpi-sync.secret-hash",()->"{argon2@SpringSecurity_v5_8}"+new com.parvez.auth.config.PasswordConfiguration().passwordEncoder().encode(SECRET));
    }
    @Test void machineTokensAreSignedNarrowlyScopedAndNeedNoHumanAccount() throws Exception {
        var response=token(SECRET,"task.metrics.read");assertThat(response.statusCode()).isEqualTo(200);
        var data=new ObjectMapper().readTree(response.body());assertThat(data.has("refresh_token")).isFalse();
        SignedJWT jwt=SignedJWT.parse(data.get("access_token").asText());
        String pem=Files.readString(TestAuthMaterial.PUBLIC_KEY).replaceAll("-----[A-Z ]+-----","").replaceAll("\\s","");
        RSAPublicKey key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        assertThat(jwt.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("kpi-sync");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("task-management");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("scope")).containsExactly("task.metrics.read");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).isEmpty();
        assertThat(token("wrong-secret","task.metrics.read").statusCode()).isEqualTo(401);
        assertThat(token(SECRET,"task.write").statusCode()).isEqualTo(400);
    }
    private HttpResponse<String> token(String secret,String scope) throws Exception {
        String basic=Base64.getEncoder().encodeToString(("kpi-sync:"+secret).getBytes(StandardCharsets.UTF_8));
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/oauth2/token")).header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope="+scope)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
