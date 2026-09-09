package com.parvez.auth.repository;

import java.util.Optional;
import java.util.UUID;
import com.parvez.auth.domain.User;
import org.springframework.data.repository.Repository;

public interface UserRepository extends Repository<User, UUID> {
    <S extends User> S save(S entity);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    long count();
}
