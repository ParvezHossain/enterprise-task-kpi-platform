package com.parvez.task.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("task.security.jwt")
public record TaskJwtProperties(String issuer, URI jwkSetUri, String audience) {
}