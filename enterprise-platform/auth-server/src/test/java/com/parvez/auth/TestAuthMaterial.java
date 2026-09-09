package com.parvez.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import com.parvez.auth.config.PasswordConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;

final class TestAuthMaterial {
    static final String TASK_SECRET = UUID.randomUUID().toString();
    static final String KPI_SECRET = UUID.randomUUID().toString();
    static final String TASK_HASH = "{argon2@SpringSecurity_v5_8}" + new PasswordConfiguration().passwordEncoder().encode(TASK_SECRET);
    static final String KPI_HASH = "{argon2@SpringSecurity_v5_8}" + new PasswordConfiguration().passwordEncoder().encode(KPI_SECRET);
    static final Path PRIVATE_KEY;
    static final Path PUBLIC_KEY;

    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("auth-test-keys-");
            directory.toFile().deleteOnExit();
            PRIVATE_KEY = directory.resolve("private.pem");
            PUBLIC_KEY = directory.resolve("public.pem");
            writePem(PRIVATE_KEY, "PRIVATE KEY", pair.getPrivate().getEncoded());
            writePem(PUBLIC_KEY, "PUBLIC KEY", pair.getPublic().getEncoded());
        } catch (Exception exception) {
            throw new ExceptionInInitializerError("Cannot create ephemeral test RSA keys");
        }
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        Files.writeString(path, "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n");
        path.toFile().deleteOnExit();
    }

    static void register(DynamicPropertyRegistry registry, String issuer) {
        registry.add("auth.signing.issuer", () -> issuer);
        registry.add("auth.signing.private-key", () -> PRIVATE_KEY.toUri().toString());
        registry.add("auth.signing.public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add("auth.signing.key-id", () -> "integration-test-key");
        registry.add("spring.flyway.placeholders.taskClientSecretHash", () -> TASK_HASH);
        registry.add("spring.flyway.placeholders.kpiClientSecretHash", () -> KPI_HASH);
    }
}
