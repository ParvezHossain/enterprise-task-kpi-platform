package com.parvez.auth.config;

import java.util.ArrayList;
import java.util.UUID;
import com.parvez.auth.security.IdentityAuthenticationService;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import com.parvez.auth.security.BoundedJdbcAuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(server -> {
            http.securityMatcher(server.getEndpointsMatcher());
            server.oidc(Customizer.withDefaults());
        });
        return http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .build();
    }

    @Bean
    @DependsOnDatabaseInitialization
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    @DependsOnDatabaseInitialization
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        // Security 7.1 defaults to its Jackson 3 mappers; no Jackson 2 override needed.
        return new BoundedJdbcAuthorizationService(jdbc, clients);
    }

    @Bean
    @DependsOnDatabaseInitialization
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(IdentityAuthenticationService identities) {
        return context -> {
            IdentityAuthenticationService.Identity identity;
            try {
                // Reload on refresh: don't grant stale roles or tokens to a disabled account.
                identity = identities.identity(UUID.fromString(context.getPrincipal().getName()));
            } catch (IllegalArgumentException | org.springframework.security.core.AuthenticationException exception) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }
            context.getClaims().subject(identity.subject());
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                // Use concrete ArrayLists supported by Security's Jackson 3 allowlist
                // when the JDBC authorization service reloads token metadata.
                context.getClaims().claim("roles", new ArrayList<>(identity.roles()))
                        .claim("authorities", new ArrayList<>(identity.authorities()));
                var audiences = new ArrayList<String>();
                if (context.getAuthorizedScopes().stream().anyMatch(scope -> scope.startsWith("task."))) audiences.add("task-management");
                if (context.getAuthorizedScopes().contains("kpi.read")) audiences.add("kpi-service");
                context.getClaims().audience(audiences);
            } else if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())
                    && context.getAuthorizedScopes().contains("email")) {
                context.getClaims().claim("email", identity.email());
            }
        };
    }
}
