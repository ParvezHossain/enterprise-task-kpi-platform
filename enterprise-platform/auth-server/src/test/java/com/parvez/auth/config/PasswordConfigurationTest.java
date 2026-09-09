package com.parvez.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordConfigurationTest {
    @Test
    void argon2idUsesFreshSaltsAndVerifiesTheExactPassword() {
        var encoder = new PasswordConfiguration().passwordEncoder();
        String password = "  Longer-password-🔐  ";
        String first = encoder.encode(password);
        String second = encoder.encode(password);

        assertThat(first).startsWith("$argon2id$v=19$").isNotEqualTo(second).hasSizeLessThanOrEqualTo(255);
        assertThat(second).startsWith("$argon2id$v=19$");
        assertThat(encoder.matches(password, first)).isTrue();
        assertThat(encoder.matches(password, second)).isTrue();
        assertThat(encoder.matches(password.strip(), first)).isFalse();
        assertThat(encoder.matches("wrong-password", first)).isFalse();
    }
}
