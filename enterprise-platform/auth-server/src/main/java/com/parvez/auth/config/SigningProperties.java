package com.parvez.auth.config;

import java.net.URI;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("auth.signing")
public record SigningProperties(@NotNull URI issuer, @NotNull Resource privateKey,
                                @NotNull Resource publicKey, @NotBlank String keyId) {
}
