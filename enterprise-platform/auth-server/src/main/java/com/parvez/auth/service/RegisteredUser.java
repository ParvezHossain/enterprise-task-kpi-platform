package com.parvez.auth.service;

import java.util.Set;
import java.util.UUID;
import com.parvez.auth.domain.RoleName;

public record RegisteredUser(UUID id, String email, boolean enabled, Set<RoleName> roles) {
    public RegisteredUser {
        roles = Set.copyOf(roles);
    }
}
