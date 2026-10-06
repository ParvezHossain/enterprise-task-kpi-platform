package com.parvez.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Explicit local setup utility; never invoked by application startup.
 */
public class PrepareDevelopment {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? ".local/auth" : args[0]).toAbsolutePath();
        if (Files.exists(directory))
            throw new IllegalStateException("Output directory already exists; keep existing keys and seed hashes on restart");
        Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        write(directory.resolve("private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        write(directory.resolve("public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        String task = secret();
        String kpi = secret();
        String password = secret();
        String sync = secret();
        var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String prefix = "{argon2@SpringSecurity_v5_8}";
        String taskHash = prefix + encoder.encode(task), kpiHash = prefix + encoder.encode(kpi), syncHash = prefix + encoder.encode(sync);
        String keyId = UUID.randomUUID().toString();
        write(directory.resolve("authorization.env"),
                "export AUTH_ISSUER='http://127.0.0.1:9000'\n"
                        + "export AUTH_RSA_PRIVATE_KEY='" + directory.resolve("private.pem").toUri() + "'\n"
                        + "export AUTH_RSA_PUBLIC_KEY='" + directory.resolve("public.pem").toUri() + "'\n"
                        + "export AUTH_RSA_KEY_ID='" + keyId + "'\n"
                        + "export AUTH_TASK_CLIENT_SECRET_HASH='" + taskHash + "'\n"
                        + "export AUTH_KPI_CLIENT_SECRET_HASH='" + kpiHash + "'\n");
        String id = UUID.randomUUID().toString();
        Path stackDirectory = directory.getParent();
        StringBuilder env = new StringBuilder();
        for (String name : new String[]{"POSTGRES_PASSWORD", "AUTH_DB_PASSWORD", "AUTH_DB_MIGRATION_PASSWORD", "TASK_DB_PASSWORD", "TASK_DB_MIGRATION_PASSWORD", "KPI_DB_PASSWORD", "KPI_DB_MIGRATION_PASSWORD"})
            env.append(name).append("='").append(secret()).append("'\n");
        env.append("AUTH_RSA_KEY_ID='").append(keyId).append("'\n")
                .append("AUTH_TASK_CLIENT_SECRET_HASH='").append(taskHash).append("'\n")
                .append("AUTH_KPI_CLIENT_SECRET_HASH='").append(kpiHash).append("'\n")
                .append("AUTH_KPI_SYNC_SECRET_HASH='").append(syncHash).append("'\n")
                .append("TASK_CLIENT_SECRET='").append(task).append("'\n")
                .append("KPI_CLIENT_SECRET='").append(kpi).append("'\n")
                .append("KPI_SYNC_SECRET='").append(sync).append("'\n");
        write(stackDirectory.resolve("stack.env"), env.toString());
        StringBuilder authSeed = new StringBuilder("BEGIN;\n"), taskSeed = new StringBuilder("BEGIN;\n"), credentials = new StringBuilder("{");
        UUID[] users = new UUID[6];
        String[] roles = {"ADMIN", "PROJECT_MANAGER", "TEAM_LEADER", "EMPLOYEE", "TEAM_LEADER", "EMPLOYEE"};
        String[] names = {"admin", "manager", "leader", "employee", "leader.two", "employee.two"};
        for (int n = 0; n < users.length; n++) {
            users[n] = UUID.nameUUIDFromBytes(("platform-local-" + names[n]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String userPassword = secret();
            authSeed.append("INSERT INTO users(id,email,password_hash) VALUES('").append(users[n]).append("','dev.").append(names[n]).append("@example.org','").append(encoder.encode(userPassword)).append("') ON CONFLICT(id) DO NOTHING;\n")
                    .append("INSERT INTO user_roles(user_id,role_id) SELECT '").append(users[n]).append("',id FROM roles WHERE name='").append(roles[n]).append("' ON CONFLICT DO NOTHING;\n");
            if (n > 0) credentials.append(",");
            credentials.append("\n\"").append(names[n]).append("\":{\"subject\":\"").append(users[n]).append("\",\"email\":\"dev.").append(names[n]).append("@example.org\",\"password\":\"").append(userPassword).append("\"}");
        }
        UUID[] teams = {UUID.nameUUIDFromBytes("platform-team-one".getBytes()), UUID.nameUUIDFromBytes("platform-team-two".getBytes())};
        UUID[] projects = {UUID.nameUUIDFromBytes("platform-project-one".getBytes()), UUID.nameUUIDFromBytes("platform-project-two".getBytes())};
        for (int n = 0; n < 2; n++) {
            taskSeed.append("INSERT INTO teams(id,name) VALUES('").append(teams[n]).append("','").append(n == 0 ? "Product Engineering" : "Operations").append("') ON CONFLICT DO NOTHING;\n")
                    .append("INSERT INTO projects(id,team_id,name) VALUES('").append(projects[n]).append("','").append(teams[n]).append("','").append(n == 0 ? "Platform Launch" : "Reliability").append("') ON CONFLICT DO NOTHING;\n");
            for (int u : new int[]{n == 0 ? 2 : 4, n == 0 ? 3 : 5})
                taskSeed.append("INSERT INTO team_memberships(team_id,user_id) VALUES('").append(teams[n]).append("','").append(users[u]).append("') ON CONFLICT DO NOTHING;\n");
            taskSeed.append("INSERT INTO team_leaderships(team_id,user_id) VALUES('").append(teams[n]).append("','").append(users[n == 0 ? 2 : 4]).append("') ON CONFLICT DO NOTHING;\n");
        }
        String[] statuses = {"DRAFT", "APPROVED", "IN_PROGRESS", "COMPLETED", "CLOSED"}, priorities = {"LOW", "MEDIUM", "HIGH", "URGENT"};
        for (int n = 0; n < 80; n++) {
            int team = n % 2;
            String status = statuses[n % 5];
            boolean complete = status.equals("COMPLETED") || status.equals("CLOSED");
            UUID taskId = UUID.nameUUIDFromBytes(("platform-seed-task-" + n).getBytes());
            String created = "CURRENT_TIMESTAMP-INTERVAL '" + (n + 3) + " days'";
            String due = complete ? (n % 3 == 0 ? "CURRENT_DATE-" + (n + 2) : "CURRENT_DATE-" + n) : (n % 3 == 0 ? "CURRENT_DATE-2" : "CURRENT_DATE+7");
            String assignee = status.equals("DRAFT") ? "NULL" : "'" + users[team == 0 ? 3 : 5] + "'";
            taskSeed.append("INSERT INTO tasks(id,title,description,status,priority,assigned_to,team_id,project_id,created_by,due_date,created_at,updated_at,started_at,completed_at,closed_at) VALUES('").append(taskId).append("','").append(team == 0 ? "Product delivery " : "Operations review ").append(n + 1).append("','Development-only sample task','").append(status).append("','").append(priorities[n % 4]).append("',").append(assignee).append(",'").append(teams[team]).append("','").append(projects[team]).append("','").append(users[1]).append("',").append(due).append(",").append(created).append(",").append(created).append(",")
                    .append(status.equals("IN_PROGRESS") || complete ? created + "+INTERVAL '2 hours'" : "NULL").append(",").append(complete ? created + "+INTERVAL '" + (8 + n % 12) + " hours'" : "NULL").append(",").append(status.equals("CLOSED") ? created + "+INTERVAL '24 hours'" : "NULL").append(") ON CONFLICT DO NOTHING;\n");
            int last = java.util.Arrays.asList(statuses).indexOf(status);
            for (int stage = 0; stage <= last; stage++) {
                UUID audit = UUID.nameUUIDFromBytes(("platform-audit-" + n + "-" + stage).getBytes());
                String action = new String[]{"CREATED", "APPROVED", "STARTED", "COMPLETED", "CLOSED"}[stage];
                UUID actor = stage == 0 || stage == 4 ? users[1] : stage == 1 ? users[team == 0 ? 2 : 4] : users[team == 0 ? 3 : 5];
                taskSeed.append("INSERT INTO task_audit_log(id,task_id,actor_id,action,previous_status,new_status,occurred_at,metadata) VALUES('").append(audit).append("','").append(taskId).append("','").append(actor).append("','").append(action).append("',").append(stage == 0 ? "NULL" : "'" + statuses[stage - 1] + "'").append(",'").append(statuses[stage]).append("',").append(created).append("+INTERVAL '").append(stage).append(" hours','{\"developmentSeed\":true}') ON CONFLICT DO NOTHING;\n");
            }
            if (last >= 1)
                taskSeed.append("INSERT INTO task_audit_log(id,task_id,actor_id,action,previous_status,new_status,occurred_at,metadata) VALUES('").append(UUID.nameUUIDFromBytes(("platform-assignment-" + n).getBytes())).append("','").append(taskId).append("','").append(users[team == 0 ? 2 : 4]).append("','ASSIGNED','APPROVED','APPROVED',").append(created).append("+INTERVAL '90 minutes','{\"developmentSeed\":true}') ON CONFLICT DO NOTHING;\n");
        }
        authSeed.append("COMMIT;\n");
        taskSeed.append("COMMIT;\n");
        credentials.append(",\n\"teamId\":\"").append(teams[0]).append("\",\"projectId\":\"").append(projects[0]).append("\"\n}\n");
        write(stackDirectory.resolve("auth-seed.sql"), authSeed.toString());
        write(stackDirectory.resolve("task-seed.sql"), taskSeed.toString());
        write(stackDirectory.resolve("users.json"), credentials.toString());
        // Container mount access: group-readable keys, with group 10001 applied by the setup command.

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
