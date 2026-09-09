package com.parvez.auth.repository;

import java.util.Optional;
import java.util.UUID;
import com.parvez.auth.domain.User;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends Repository<User, UUID> {
    // ON CONFLICT handles concurrent registrations without logging a PostgreSQL
    // constraint exception containing submitted values.
    @Modifying
    @Query(value = """
            INSERT INTO users (id, email, password_hash, enabled, version)
            VALUES (:id, :email, :passwordHash, true, 0)
            ON CONFLICT (email) DO NOTHING
            """, nativeQuery = true)
    int insertRegisteredUser(UUID id, String email, String passwordHash);

    <S extends User> S save(S entity);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    long count();
}
