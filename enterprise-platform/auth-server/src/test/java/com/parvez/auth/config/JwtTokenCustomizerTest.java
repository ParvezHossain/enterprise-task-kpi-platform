package com.parvez.auth.config;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.parvez.auth.security.IdentityAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JwtTokenCustomizerTest {
    private final IdentityAuthenticationService identities = mock(IdentityAuthenticationService.class);
    private final org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer<JwtEncodingContext>
            customizer = new AuthorizationServerConfig().jwtTokenCustomizer(identities);
    private final UUID subject = UUID.randomUUID();

    @Test
    void accessAudienceComesOnlyFromAuthorizedScopesAndIdentityIsReloaded() {
        when(identities.identity(subject)).thenReturn(
                new IdentityAuthenticationService.Identity(subject.toString(), "user@example.org",
                        List.of("ROLE_EMPLOYEE", "task.read")),
                new IdentityAuthenticationService.Identity(subject.toString(), "user@example.org",
                        List.of("ROLE_MANAGER", "kpi.read")));
        var first = context(OAuth2TokenType.ACCESS_TOKEN, Set.of("openid", "task.read"));
        customizer.customize(first);
        var firstClaims = first.getClaims().build().getClaims();
        assertThat(firstClaims).containsEntry("sub", subject.toString())
                .containsEntry("aud", List.of("task-management"))
                .containsEntry("roles", List.of("EMPLOYEE"))
                .containsEntry("authorities", List.of("ROLE_EMPLOYEE", "task.read"))
                .doesNotContainKeys("email", "password", "password_hash");

        var refreshed = context(OAuth2TokenType.ACCESS_TOKEN, Set.of("openid", "kpi.read"));
        customizer.customize(refreshed);
        assertThat(refreshed.getClaims().build().getClaims())
                .containsEntry("aud", List.of("kpi-service"))
                .containsEntry("roles", List.of("MANAGER"))
                .containsEntry("authorities", List.of("ROLE_MANAGER", "kpi.read"));
        verify(identities, times(2)).identity(subject);
    }

    @Test
    void ungrantedScopesDoNotProduceServiceAudiences() {
        when(identities.identity(subject)).thenReturn(
                new IdentityAuthenticationService.Identity(subject.toString(), "user@example.org", List.of("ROLE_EMPLOYEE")));
        var context = context(OAuth2TokenType.ACCESS_TOKEN, Set.of("openid", "profile"));
        customizer.customize(context);
        assertThat(context.getClaims().build().getAudience()).isEmpty();
        var both = context(OAuth2TokenType.ACCESS_TOKEN, Set.of("task.read", "kpi.read"));
        customizer.customize(both);
        assertThat(both.getClaims().build().getAudience()).containsExactly("task-management", "kpi-service");
    }

    @Test
    void idTokenEmailRequiresConsentScopeAndPreservesClientAudienceAndNonce() {
        when(identities.identity(subject)).thenReturn(
                new IdentityAuthenticationService.Identity(subject.toString(), "user@example.org", List.of("ROLE_EMPLOYEE")));
        for (var scopes : List.of(Set.of("openid"), Set.of("openid", "email"))) {
            var context = context(new OAuth2TokenType("id_token"), scopes);
            context.getClaims().audience(List.of("task-management-ui")).claim("nonce", "request-nonce");
            customizer.customize(context);
            var claims = context.getClaims().build().getClaims();
            assertThat(claims).containsEntry("sub", subject.toString())
                    .containsEntry("aud", List.of("task-management-ui")).containsEntry("nonce", "request-nonce")
                    .doesNotContainKeys("roles", "authorities", "password", "password_hash", "email_verified");
            if (scopes.contains("email")) assertThat(claims).containsEntry("email", "user@example.org");
            else assertThat(claims).doesNotContainKey("email");
        }
    }

    @Test
    void unavailableIdentityAndMalformedPrincipalFailClosedWithoutLeakingDetails() {
        when(identities.identity(subject)).thenThrow(new UsernameNotFoundException("sensitive-identity-detail"));
        assertInvalidGrant(context(OAuth2TokenType.ACCESS_TOKEN, Set.of("task.read")));
        var malformed = JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
                .principal(new UsernamePasswordAuthenticationToken("invalid-uuid", null))
                .tokenType(OAuth2TokenType.ACCESS_TOKEN).authorizedScopes(Set.of("task.read")).build();
        assertInvalidGrant(malformed);
        verify(identities).identity(subject);
    }

    private void assertInvalidGrant(JwtEncodingContext context) {
        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception -> {
                    assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(exception).hasNoCause().hasMessageNotContaining("sensitive-identity-detail");
                });
    }

    private JwtEncodingContext context(OAuth2TokenType type, Set<String> scopes) {
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
                .principal(new UsernamePasswordAuthenticationToken(subject.toString(), null))
                .tokenType(type).authorizedScopes(scopes).build();
    }
}
