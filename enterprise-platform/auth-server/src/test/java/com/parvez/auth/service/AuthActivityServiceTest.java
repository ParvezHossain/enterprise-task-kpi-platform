package com.parvez.auth.service;

import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthActivityServiceTest {
    @Test
    void auditFailureCannotPreventLogoutAndHasAnOperationalCounter() {
        var jdbc = mock(JdbcTemplate.class);
        var metrics = new SimpleMeterRegistry();
        var service = new AuthActivityService(jdbc, metrics);
        doThrow(new DataAccessResourceFailureException("database unavailable")).when(jdbc)
                .update(anyString(), any(Object[].class));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(UUID.randomUUID().toString(), null, java.util.List.of());
        assertThatCode(() -> service.record(authentication, "AUTH_LOGOUT")).doesNotThrowAnyException();
        assertThat(metrics.get("auth.activity.write.failures").counter().count()).isEqualTo(1);
    }

    @Test
    void nonHumanPrincipalsAndInvalidPagesNeverQueryTheDatabase() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new AuthActivityService(jdbc, new SimpleMeterRegistry());
        service.record(null, "AUTH_LOGOUT");
        service.record(UsernamePasswordAuthenticationToken.authenticated("kpi-sync", null, java.util.List.of()), "AUTH_LOGOUT");
        service.failedLogin("x".repeat(255));
        assertThatCode(() -> service.history(UUID.randomUUID(), -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> service.history(UUID.randomUUID(), 0, 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
