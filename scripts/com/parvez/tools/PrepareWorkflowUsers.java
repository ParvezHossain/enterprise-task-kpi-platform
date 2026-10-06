package com.parvez.tools;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
/** Isolated development users/team/project for each real workflow run. */
public class PrepareWorkflowUsers {
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args[0]);
        Files.createDirectories(directory,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var encoder=Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        StringBuilder sql=new StringBuilder("BEGIN;\n");
        Map<String,Object> credentials=new LinkedHashMap<>();
        UUID[] users=new UUID[4];
        String[] names={"admin","manager","leader","employee"},roles={"ADMIN","PROJECT_MANAGER","TEAM_LEADER","EMPLOYEE"};
        for(int n=0;n<4;n++) {
            users[n]=UUID.randomUUID();
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
            String password=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String email="e2e."+users[n]+"@example.test";
            sql.append("INSERT INTO users(id,email,password_hash) VALUES('").append(users[n]).append("','").append(email).append("','").append(encoder.encode(password)).append("');\n")
                .append("INSERT INTO user_roles(user_id,role_id) SELECT '").append(users[n]).append("',id FROM roles WHERE name='").append(roles[n]).append("';\n");
            credentials.put(names[n],Map.of("subject",users[n].toString(),"email",email,"password",password));
        }
        UUID team=UUID.randomUUID(),project=UUID.randomUUID();
        String tasks="BEGIN;\nINSERT INTO teams(id,name) VALUES('"+team+"','E2E team');\nINSERT INTO projects(id,team_id,name) VALUES('"+project+"','"+team+"','E2E project');\n"
            +"INSERT INTO team_memberships(team_id,user_id) VALUES('"+team+"','"+users[2]+"'),('"+team+"','"+users[3]+"');\n"
            +"INSERT INTO team_leaderships(team_id,user_id) VALUES('"+team+"','"+users[2]+"');\nCOMMIT;\n";
        credentials.put("teamId",team.toString());credentials.put("projectId",project.toString());
        sql.append("COMMIT;\n");
        write(directory.resolve("auth-seed.sql"),sql.toString());
        write(directory.resolve("task-seed.sql"),tasks);
        write(directory.resolve("users.json"),new tools.jackson.databind.ObjectMapper().writeValueAsString(credentials));
    }
    static void write(Path file,String text) throws Exception {
        Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file,text);
    }
}
