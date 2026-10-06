package com.parvez.task.web;
import java.util.*;
import com.parvez.task.authorization.*;
import com.parvez.task.security.CurrentTaskActorProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/reference")
public class TaskReferenceController {
    private final JdbcTemplate jdbc;private final CurrentTaskActorProvider actors;
    public TaskReferenceController(JdbcTemplate jdbc,CurrentTaskActorProvider actors){this.jdbc=jdbc;this.actors=actors;}
    public record Team(UUID id,String name) {}
    public record Project(UUID id,UUID teamId,String name) {}
    public record Member(UUID userId) {}
    private boolean manager(TaskActor actor){return actor.roles().contains(TaskRole.ADMIN)||actor.roles().contains(TaskRole.PROJECT_MANAGER);}
    private void teamAccess(TaskActor actor,UUID team){if(!manager(actor)&&!actor.managedTeamIds().contains(team)) throw new AccessDeniedException("Team access denied");}
    private int offset(int page){if(page<0||page>100000) throw new IllegalArgumentException("Invalid page");return page*100;}
    @GetMapping("/teams") public List<Team> teams(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page){
        TaskActor actor=actors.fromJwt(jwt);
        if(manager(actor)) return jdbc.query("SELECT id,name FROM teams ORDER BY id LIMIT 100 OFFSET ?",(r,n)->new Team(r.getObject(1,UUID.class),r.getString(2)),offset(page));
        if(actor.roles().contains(TaskRole.TEAM_LEADER)) return jdbc.query("SELECT t.id,t.name FROM teams t JOIN team_leaderships l ON t.id=l.team_id WHERE l.user_id=? ORDER BY t.id LIMIT 100 OFFSET ?",(r,n)->new Team(r.getObject(1,UUID.class),r.getString(2)),UUID.fromString(actor.subject()),offset(page));
        if(actor.roles().contains(TaskRole.EMPLOYEE)) return jdbc.query("SELECT t.id,t.name FROM teams t JOIN team_memberships m ON t.id=m.team_id WHERE m.user_id=? ORDER BY t.id LIMIT 100 OFFSET ?",(r,n)->new Team(r.getObject(1,UUID.class),r.getString(2)),UUID.fromString(actor.subject()),offset(page));
        throw new AccessDeniedException("Role required");
    }
    @GetMapping("/projects") public List<Project> projects(@AuthenticationPrincipal Jwt jwt,@RequestParam UUID teamId,@RequestParam(defaultValue="0") int page){
        teamAccess(actors.fromJwt(jwt),teamId);return jdbc.query("SELECT id,team_id,name FROM projects WHERE team_id=? ORDER BY id LIMIT 100 OFFSET ?",(r,n)->new Project(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)),teamId,offset(page));
    }
    @GetMapping("/members") public List<Member> members(@AuthenticationPrincipal Jwt jwt,@RequestParam UUID teamId,@RequestParam(defaultValue="0") int page){
        TaskActor actor=actors.fromJwt(jwt);if(!(actor.roles().contains(TaskRole.ADMIN)||actor.managedTeamIds().contains(teamId))) throw new AccessDeniedException("Assignment access denied");
        return jdbc.query("SELECT user_id FROM team_memberships WHERE team_id=? ORDER BY user_id LIMIT 100 OFFSET ?",(r,n)->new Member(r.getObject(1,UUID.class)),teamId,offset(page));
    }
}
