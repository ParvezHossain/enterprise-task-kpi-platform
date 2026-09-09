package com.parvez.auth.config;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningConfigurationTest {
    private final SigningConfiguration config = new SigningConfiguration();

    @Test
    void rejectsMalformedMismatchedAndWeakKeysWithoutExposingMaterial() throws Exception {
        var pair = pair(2048);
        var other = pair(2048);
        var weak = pair(1024);
        var malformed = new SigningProperties(URI.create("https://auth.example.org"),
                new ByteArrayResource("secret-canary-not-a-key".getBytes()), pem("PUBLIC KEY", pair.getPublic().getEncoded()), "test");
        for (var properties : new SigningProperties[]{malformed, properties(pair, other), properties(weak, weak)}) {
            assertThatThrownBy(() -> config.jwkSource(properties)).isInstanceOf(IllegalStateException.class)
                    .hasNoCause().hasMessageNotContaining("secret-canary");
        }
        assertThat(config.jwkSource(properties(pair, pair))).isNotNull();
    }

    @Test
    void issuerMustBeExplicitHttpsOrLoopbackHttp() {
        for (String issuer : new String[]{"http://auth.example.org", "https://auth.example.org?secret=canary", "https://user:secret@auth.example.org"}) {
            var properties = new SigningProperties(URI.create(issuer), null, null, "test");
            assertThatThrownBy(() -> config.authorizationServerSettings(properties)).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("canary").hasMessageNotContaining("user:secret");
        }
        assertThat(config.authorizationServerSettings(new SigningProperties(URI.create("https://auth.example.org"), null, null, "test"))
                .getIssuer()).isEqualTo("https://auth.example.org");
    }

    private static SigningProperties properties(KeyPair privatePair, KeyPair publicPair) {
        return new SigningProperties(URI.create("https://auth.example.org"), pem("PRIVATE KEY", privatePair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", publicPair.getPublic().getEncoded()), "test");
    }
    private static ByteArrayResource pem(String type, byte[] value) {
        return new ByteArrayResource(("-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(value)
                + "\n-----END " + type + "-----\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static KeyPair pair(int size) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(size);
        return generator.generateKeyPair();
    }
}
