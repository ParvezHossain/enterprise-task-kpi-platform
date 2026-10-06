package com.parvez.kpi.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("bff")
public class BffConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain browserSecurity(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
        return http.securityMatcher("/bff/**", "/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(a -> a.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll().anyRequest().authenticated())
                .oauth2Login(login -> login.defaultSuccessUrl("/", true).authorizationEndpoint(endpoint -> {
                    var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
                    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
                    endpoint.authorizationRequestResolver(resolver);
                }))
                .logout(logout -> logout.logoutUrl("/bff/logout").invalidateHttpSession(true).deleteCookies("KPI_SESSION").logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(e -> e.authenticationEntryPoint((r, s, x) -> com.parvez.kpi.web.KpiProblemResponses.write(r, s, 401, "Unauthorized")).accessDeniedHandler((r, s, x) -> com.parvez.kpi.web.KpiProblemResponses.write(r, s, 403, "Forbidden")))
                .headers(h -> h.referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)).contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'")))
                .build();
    }

    @Bean
    ClientRegistrationRepository browserClients(@Value("${bff.issuer}") String issuer, @Value("${bff.auth-internal-url}") String internal, @Value("${bff.client-secret}") String secret, @Value("${bff.redirect-uri}") String redirect) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("kpi-ui").clientId("kpi-ui").clientSecret(secret)
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE).scope("openid", "profile", "email", "kpi.read")
                .redirectUri(redirect).authorizationUri(issuer + "/oauth2/authorize").tokenUri(internal + "/oauth2/token").jwkSetUri(internal + "/oauth2/jwks").issuerUri(issuer)
                .userInfoUri(internal + "/userinfo").userNameAttributeName("sub").clientName("kpi-ui").build());
    }

    @Bean
    OAuth2AuthorizedClientManager clientManager(ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository repository) {
        var manager = new DefaultOAuth2AuthorizedClientManager(registrations, repository);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build());
        return manager;
    }
}
