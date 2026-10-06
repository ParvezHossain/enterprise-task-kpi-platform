package com.parvez.kpi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class KpiSecurityConfiguration {
    @Bean
    JwtDecoder jwtDecoder(@Value("${kpi.security.issuer}") String issuer, @Value("${kpi.security.jwk-set-uri}") String keys) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), jwt -> jwt.getAudience().contains("kpi-service") ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .exceptionHandling(e -> e.authenticationEntryPoint((r, s, x) -> com.parvez.kpi.web.KpiProblemResponses.write(r, s, 401, "Unauthorized"))
                        .accessDeniedHandler((r, s, x) -> com.parvez.kpi.web.KpiProblemResponses.write(r, s, 403, "Forbidden")))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/info").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.decoder(decoder))).build();
    }
}
