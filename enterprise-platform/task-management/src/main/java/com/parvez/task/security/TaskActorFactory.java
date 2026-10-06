package com.parvez.task.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.parvez.task.authorization.TaskActor;
import com.parvez.task.authorization.TaskRole;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TaskActorFactory {
    public TaskActor fromJwt(Jwt jwt, Set<UUID> managedTeamIds) {
        EnumSet<TaskRole> roles = EnumSet.noneOf(TaskRole.class);
        Object rolesClaim = jwt.getClaims().get("roles");
        if (rolesClaim instanceof Collection<?> roleNames) {
            for (Object roleName : roleNames) {
                if (roleName instanceof String name) {
                    try {
                        roles.add(TaskRole.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown roles fail closed and do not grant policy actions.
                    }
                }
            }
        }
        return new TaskActor(jwt.getSubject(), roles, managedTeamIds);
    }
}