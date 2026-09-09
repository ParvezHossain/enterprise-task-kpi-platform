package com.parvez.auth.service;

import java.util.Set;
import java.util.UUID;
import com.parvez.auth.domain.RoleName;
import com.parvez.auth.repository.RoleRepository;
import com.parvez.auth.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import static com.parvez.auth.service.RegistrationException.Reason.*;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final TransactionTemplate transaction;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwords,
                       Validator validator, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.validator = validator;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public RegisteredUser register(RegistrationRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            // ConstraintViolationException retains rejected values, including passwords.
            throw new RegistrationException(INVALID_INPUT);
        }
        String hash = passwords.encode(request.password());
        try {
            return transaction.execute(status -> {
                var employee = roles.findByName(RoleName.EMPLOYEE)
                        .orElseThrow(() -> new RegistrationException(ROLE_UNAVAILABLE));
                UUID id = UUID.randomUUID();
                if (users.insertRegisteredUser(id, request.email(), hash) == 0) {
                    throw new RegistrationException(EMAIL_UNAVAILABLE);
                }
                var user = users.findById(id).orElseThrow(() -> new RegistrationException(PERSISTENCE_FAILURE));
                user.assignRole(employee);
                return new RegisteredUser(user.getId(), user.getEmail(), user.isEnabled(), Set.of(employee.getName()));
            });
        } catch (DataAccessException | TransactionException exception) {
            // Include commit-time failures, but never propagate database row details.
            throw new RegistrationException(PERSISTENCE_FAILURE);
        }
    }
}
