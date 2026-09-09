package com.parvez.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Explicit local setup utility; never invoked by application startup. */
public class PrepareDevelopment {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? ".local/auth" : args[0]).toAbsolutePath();
        if (Files.exists(directory)) throw new IllegalStateException("Output directory already exists; keep existing keys and seed hashes on restart");
        Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        write(directory.resolve("private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        write(directory.resolve("public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        String task = secret();
        String kpi = secret();
        String password = secret();
        var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String prefix = "{argon2@SpringSecurity_v5_8}";
        write(directory.resolve("authorization.env"),
                "export AUTH_ISSUER='http://127.0.0.1:9000'\n"
                + "export AUTH_RSA_PRIVATE_KEY='" + directory.resolve("private.pem").toUri() + "'\n"
                + "export AUTH_RSA_PUBLIC_KEY='" + directory.resolve("public.pem").toUri() + "'\n"
                + "export AUTH_RSA_KEY_ID='" + UUID.randomUUID() + "'\n"
                + "export AUTH_TASK_CLIENT_SECRET_HASH='" + prefix + encoder.encode(task) + "'\n"
                + "export AUTH_KPI_CLIENT_SECRET_HASH='" + prefix + encoder.encode(kpi) + "'\n");
        String id = UUID.randomUUID().toString();
        write(directory.resolve("bootstrap-user.sql"), """
                -- Explicit development-only provisioning. Run after local,dev startup.
                -- Fails atomically if EMPLOYEE is absent; never modifies existing users.
                BEGIN;
                INSERT INTO users(id,email,password_hash) VALUES
                ('%s','dev.employee@example.org','%s');
                INSERT INTO user_roles(user_id,role_id) VALUES
                ('%s',(SELECT id FROM roles WHERE name='EMPLOYEE'));
                COMMIT;
                """.formatted(id, encoder.encode(password), id));
        write(directory.resolve("http-client.private.env.json"), """
                {
                  "task": {
                    "issuer": "http://127.0.0.1:9000",
                    "client_id": "task-management-ui",
                    "client_secret": "%s",
                    "redirect_uri": "http://127.0.0.1:8080/login/oauth2/code/task-management-ui",
                    "resource_scope": "task.read",
                    "username": "dev.employee@example.org",
                    "password": "%s"
                  },
                  "kpi": {
                    "issuer": "http://127.0.0.1:9000",
                    "client_id": "kpi-ui",
                    "client_secret": "%s",
                    "redirect_uri": "http://127.0.0.1:8081/login/oauth2/code/kpi-ui",
                    "resource_scope": "kpi.read",
                    "username": "dev.employee@example.org",
                    "password": "%s"
                  }
                }
                """.formatted(task, password, kpi, password));
        System.out.println("Development material written to " + directory + "; no secrets printed. Follow docs/development.md.");
    }

    private static String secret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
    private static void write(Path path, String content) throws Exception {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(path, content);
    }
}
