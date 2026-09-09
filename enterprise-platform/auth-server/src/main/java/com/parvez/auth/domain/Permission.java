package com.parvez.auth.domain;

import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected Permission() {
    }

    public Permission(String name) {
        this.name = Objects.requireNonNull(name).strip();
        if (this.name.isEmpty() || this.name.length() > 100) {
            throw new IllegalArgumentException("Permission name must contain 1 to 100 characters");
        }
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
