package com.parvez.auth.security;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.parvez.auth.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAuthenticationService implements UserDetailsService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public IdentityAuthenticationService(UserRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        if (email == null || email.length() > 254) throw unavailable();
        var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(this::unavailable);
        return User.withUsername(user.getId().toString()).password(user.getPasswordHash())
                .disabled(!user.isEnabled()).authorities(authorities(user.getId()).toArray(String[]::new)).build();
    }

    @Transactional(readOnly = true)
    public Identity identity(UUID id) {
        var user = users.findById(id).filter(com.parvez.auth.domain.User::isEnabled).orElseThrow(this::unavailable);
        return new Identity(user.getId().toString(), user.getEmail(), authorities(id));
    }

    private List<String> authorities(UUID id) {
        // Bound permission expansion too; a single identity may have many permissions.
        var values = jdbc.queryForList("""
                SELECT authority FROM (
                    SELECT 'ROLE_' || r.name AS authority FROM roles r
                    JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ?
                    UNION
                    SELECT p.name AS authority FROM permissions p
                    JOIN role_permissions rp ON rp.permission_id = p.id
                    JOIN user_roles ur ON ur.role_id = rp.role_id WHERE ur.user_id = ?
                ) a ORDER BY authority LIMIT 257
                """, String.class, id, id);
        if (values.size() > 256) throw unavailable();
        return values;
    }

    private UsernameNotFoundException unavailable() {
        return new UsernameNotFoundException("Identity unavailable");
    }

    public record Identity(String subject, String email, List<String> authorities) {
        public List<String> roles() {
            return authorities.stream().filter(value -> value.startsWith("ROLE_"))
                    .map(value -> value.substring(5)).toList();
        }
    }
}
