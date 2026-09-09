package com.parvez.auth.service;

import java.util.Optional;
import com.parvez.auth.domain.Role;
import com.parvez.auth.domain.RoleName;
import com.parvez.auth.repository.RoleRepository;
import com.parvez.auth.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static com.parvez.auth.service.RegistrationException.Reason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private ValidatorFactory validation;
    private UserService service;

    @BeforeEach
    void setUp() {
        validation = Validation.buildDefaultValidatorFactory();
        service = new UserService(users, roles, passwords, validation.getValidator(), transactions);
    }

    @AfterEach
    void closeValidator() { validation.close(); }

    @Test
    void invalidInputIsRejectedBeforeHashingOrPersistence() {
        RegistrationRequest[] invalid = { null, new RegistrationRequest(null, "valid-password"),
                new RegistrationRequest("invalid", "valid-password"),
                new RegistrationRequest("valid@example.org", null),
                new RegistrationRequest("valid@example.org", "short"),
                new RegistrationRequest("valid@example.org", " ".repeat(12)),
                new RegistrationRequest("valid@example.org", "x".repeat(129)),
                new RegistrationRequest("x".repeat(250) + "@example.org", "valid-password") };
        for (var request : invalid) {
            assertThatThrownBy(() -> service.register(request)).isInstanceOfSatisfying(
                    RegistrationException.class, exception -> assertThat(exception.getReason()).isEqualTo(INVALID_INPUT));
        }
        verifyNoInteractions(users, roles, passwords, transactions);
    }

    @Test
    void requestNormalizesOnlyEmailAndRedactsItsStringRepresentation() {
        var request = new RegistrationRequest(" User@Example.org ", "  valid-password  ");
        assertThat(request.email()).isEqualTo("user@example.org");
        assertThat(request.password()).isEqualTo("  valid-password  ");
        assertThat(request.toString()).doesNotContain(request.email(), request.password());
    }

    @Test
    void missingEmployeeRoleFailsClosed() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(passwords.encode(anyString())).thenReturn("encoded");
        when(roles.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(new RegistrationRequest("user@example.org", "valid-password")))
                .isInstanceOfSatisfying(RegistrationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(ROLE_UNAVAILABLE));
        verifyNoInteractions(users);
        verify(transactions).rollback(any());
    }

    @Test
    void persistenceFailureDiscardsSensitiveCauseAndRollsBack() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(passwords.encode(anyString())).thenReturn("secret-hash");
        when(roles.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(new Role(RoleName.EMPLOYEE)));
        when(users.insertRegisteredUser(any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("secret-hash secret-token"));
        assertThatThrownBy(() -> service.register(new RegistrationRequest("user@example.org", "valid-password")))
                .isInstanceOfSatisfying(RegistrationException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(PERSISTENCE_FAILURE);
                    assertThat(exception).hasNoCause().hasMessageNotContaining("secret");
                });
        verify(transactions).rollback(any());
    }
}
