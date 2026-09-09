package com.parvez.auth;

import com.parvez.auth.domain.Permission;
import com.parvez.auth.domain.RoleName;
import com.parvez.auth.domain.User;
import com.parvez.auth.repository.PermissionRepository;
import com.parvez.auth.repository.RoleRepository;
import com.parvez.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IdentityRepositoryIT extends PostgresRepositoryTestSupport {
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired Flyway flyway;

    @Test
    void developmentRolesAreQueryableAndMigrationsAreNotReapplied() {
        assertThat(roles.count()).isEqualTo(4);
        for (RoleName name : RoleName.values()) {
            assertThat(roles.findByName(name)).get().extracting(role -> role.getName()).isEqualTo(name);
        }
        assertThat(users.count()).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(roles.count()).isEqualTo(4);
    }

    @Test
    @Transactional
    void userRoleAndPermissionAssociationsRoundTrip() {
        var permission = permissions.save(new Permission("repository.test"));
        var employee = roles.findByName(RoleName.EMPLOYEE).orElseThrow();
        employee.grantPermission(permission);
        var user = new User(" Employee@Example.org ", "test-only-encoded-password");
        user.assignRole(employee);
        users.save(user);
        entityManager.flush();
        var userId = user.getId();
        entityManager.clear();

        var stored = users.findByEmail("employee@example.org").orElseThrow();
        assertThat(stored.getId()).isEqualTo(userId);
        assertThat(stored.isEnabled()).isTrue();
        assertThat(stored.getRoles()).singleElement().satisfies(role -> {
            assertThat(role.getName()).isEqualTo(RoleName.EMPLOYEE);
            assertThat(role.getPermissions()).extracting(Permission::getName).contains("repository.test");
        });
        assertThat(permissions.findByName("repository.test")).isPresent();
    }

    @Test
    @Transactional
    void canonicalEmailUniquenessIsEnforcedByPostgres() {
        users.save(new User("same@example.org", "test-only-encoded-password"));
        entityManager.flush();
        users.save(new User(" SAME@EXAMPLE.ORG ", "another-test-only-encoded-password"));
        assertThatThrownBy(entityManager::flush).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }
}
