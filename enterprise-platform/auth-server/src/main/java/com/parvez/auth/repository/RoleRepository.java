package com.parvez.auth.repository;

import java.util.Optional;
import java.util.UUID;
import com.parvez.auth.domain.Role;
import com.parvez.auth.domain.RoleName;
import org.springframework.data.repository.Repository;

public interface RoleRepository extends Repository<Role, UUID> {
    <S extends Role> S save(S entity);
    Optional<Role> findById(UUID id);
    Optional<Role> findByName(RoleName name);
    long count();
}
