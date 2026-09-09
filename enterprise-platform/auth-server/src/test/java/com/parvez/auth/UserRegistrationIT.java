package com.parvez.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.parvez.auth.domain.RoleName;
import com.parvez.auth.repository.UserRepository;
import com.parvez.auth.service.RegistrationException;
import com.parvez.auth.service.RegistrationRequest;
import com.parvez.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static com.parvez.auth.service.RegistrationException.Reason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "logging.level.com.parvez.auth=TRACE")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UserRegistrationIT extends PostgresRepositoryTestSupport {
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registrationAndFailuresNeverLogPasswordsHashesOrContextTokens(CapturedOutput output) {
        String password = "Password-canary-" + UUID.randomUUID();
        String accessToken = "Access-token-canary-" + UUID.randomUUID();
        String refreshToken = "Refresh-token-canary-" + UUID.randomUUID();
        var authentication = UsernamePasswordAuthenticationToken.authenticated("registration-test", accessToken, List.of());
        authentication.setDetails(Map.of("refresh_token", refreshToken));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var logger = LoggerFactory.getLogger(UserRegistrationIT.class);
        try {
            logger.info("registration-log-capture-control");
            var request = new RegistrationRequest(" Log-User@Example.org ", password);
            var registered = service.register(request);
            var stored = users.findById(registered.id()).orElseThrow();
            assertThat(registered.email()).isEqualTo("log-user@example.org");
            assertThat(registered.enabled()).isTrue();
            assertThat(registered.roles()).containsExactly(RoleName.EMPLOYEE);
            assertThat(passwords.matches(password, stored.getPasswordHash())).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id = ?", Long.class,
                    registered.id())).isEqualTo(1L);
            assertThat(registered.toString()).doesNotContain(password, stored.getPasswordHash());
            logger.debug("Registration input: {}", request);
            assertThatThrownBy(() -> service.register(request)).isInstanceOfSatisfying(RegistrationException.class,
                    exception -> {
                        assertThat(exception.getReason()).isEqualTo(EMAIL_UNAVAILABLE);
                        logger.warn("Registration rejected", exception);
                    });
            assertThatThrownBy(() -> service.register(new RegistrationRequest("invalid", password)))
                    .isInstanceOfSatisfying(RegistrationException.class, exception -> {
                        assertThat(exception.getReason()).isEqualTo(INVALID_INPUT);
                        logger.warn("Registration rejected", exception);
                    });
            // A CHECK failure would normally include the entire rejected row and its hash.
            jdbc.execute("ALTER TABLE users ADD CONSTRAINT test_registration_failure CHECK (email <> 'failure@example.org')");
            try {
                assertThatThrownBy(() -> service.register(new RegistrationRequest("failure@example.org", password)))
                        .isInstanceOfSatisfying(RegistrationException.class, exception -> {
                            assertThat(exception.getReason()).isEqualTo(PERSISTENCE_FAILURE);
                            assertThat(exception).hasNoCause();
                            logger.warn("Registration rejected", exception);
                        });
                assertThat(users.findByEmail("failure@example.org")).isEmpty();
            } finally {
                jdbc.execute("ALTER TABLE users DROP CONSTRAINT test_registration_failure");
            }
            assertThat(output.getAll()).contains("registration-log-capture-control", "Registration input: RegistrationRequest[REDACTED]")
                    .doesNotContain(password, accessToken, refreshToken, stored.getPasswordHash(), "$argon2id$");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void simultaneousRegistrationsHaveExactlyOneWinner() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try {
                    service.register(new RegistrationRequest(index == 0 ? "Race@Example.org" : " race@example.org ",
                            "Concurrency-password-" + index));
                    return "created";
                } catch (RegistrationException exception) {
                    return exception.getReason().name();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(20, TimeUnit.SECONDS), attempts.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", EMAIL_UNAVAILABLE.name());
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'race@example.org'", Long.class)).isEqualTo(1L);
    }
}
