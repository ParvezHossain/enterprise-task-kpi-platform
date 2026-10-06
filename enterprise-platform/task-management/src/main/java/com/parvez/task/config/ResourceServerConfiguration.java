package com.parvez.task.config;

import com.parvez.task.domain.TaskStateMachine;
import com.parvez.task.query.TaskQueryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({TaskJwtProperties.class, TaskQueryProperties.class})
@EnableMethodSecurity
public class ResourceServerConfiguration {
    @Bean
    TaskStateMachine taskStateMachine() {
        return new TaskStateMachine();
    }

    @Bean
        @Profile("!local-stub")
    JwtDecoder jwtDecoder(TaskJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString()).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audienceValidator));
        return decoder;
    }

    @org.springframework.beans.factory.annotation.Value("${task.cors.allowed-origins:http://127.0.0.1:8080}")
    private java.util.List<String> allowedOrigins;
    @Bean
    org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        if(allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) throw new IllegalArgumentException("Wildcard CORS origins are forbidden");
        var cors=new org.springframework.web.cors.CorsConfiguration();cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(java.util.List.of("GET","POST","PATCH","OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization","Content-Type","Idempotency-Key","X-Request-ID"));
        cors.setExposedHeaders(java.util.List.of("X-Request-ID","Location","Retry-After"));cors.setAllowCredentials(false);
        var source=new org.springframework.web.cors.UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",cors);return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                    .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,error) -> com.parvez.task.web.TaskProblemResponses.write(request,response,401,"Unauthorized"))
                    .accessDeniedHandler((request,response,error) -> com.parvez.task.web.TaskProblemResponses.write(request,response,403,"Forbidden")))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }
}