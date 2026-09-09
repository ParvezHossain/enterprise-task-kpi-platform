package com.parvez.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Security 7.1 supports Argon2id with Bouncy Castle; v5_8 names the parameter
        // preset, not an obsolete API. Keep its random salt and encoded cost metadata.
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
