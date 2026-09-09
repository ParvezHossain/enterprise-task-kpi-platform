package com.parvez.auth.repository;

import java.util.Optional;
import java.util.UUID;
import com.parvez.auth.domain.Permission;
import org.springframework.data.repository.Repository;

public interface PermissionRepository extends Repository<Permission, UUID> {
    <S extends Permission> S save(S entity);
    Optional<Permission> findById(UUID id);
    Optional<Permission> findByName(String name);
    long count();
}
