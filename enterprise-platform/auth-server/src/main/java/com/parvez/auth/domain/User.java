package com.parvez.auth.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
    }

    /** Accepts an already encoded password; password encoding belongs to the user service. */
    public User(String email, String passwordHash) {
        this.email = Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        if (this.email.isEmpty() || this.email.length() > 254) {
            throw new IllegalArgumentException("Email must contain 1 to 254 characters");
        }
        if (passwordHash.isBlank() || passwordHash.length() > 255) {
            throw new IllegalArgumentException("Encoded password must contain 1 to 255 characters");
        }
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public long getVersion() { return version; }
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }
    public void assignRole(Role role) { roles.add(Objects.requireNonNull(role)); }
}
