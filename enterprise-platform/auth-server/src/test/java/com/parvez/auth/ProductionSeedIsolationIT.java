package com.parvez.auth;

import com.parvez.auth.repository.PermissionRepository;
import com.parvez.auth.repository.RoleRepository;
import com.parvez.auth.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"prod", "dev", "test"})
class ProductionSeedIsolationIT extends PostgresRepositoryTestSupport {
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired UserRepository users;
    @Autowired Flyway flyway;

    @Test
    void productionSchemaValidatesWithoutDevelopmentSeedEvenWithMixedProfiles() {
        assertThat(roles.count()).isZero();
        assertThat(permissions.count()).isZero();
        assertThat(users.count()).isZero();
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().applied()[0].getVersion().toString()).isEqualTo("1");
    }
}
