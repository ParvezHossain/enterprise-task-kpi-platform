package com.parvez.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http,
            com.parvez.auth.service.AuthActivityService activities) throws Exception {
        var browserLogin = new org.springframework.security.web.util.matcher.MediaTypeRequestMatcher(
                org.springframework.http.MediaType.TEXT_HTML);
        browserLogin.setIgnoredMediaTypes(java.util.Set.of(org.springframework.http.MediaType.ALL));
        return http
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator", "/actuator/**").authenticated()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/account-assets/account.css",
                                "/account-assets/account.js", "/account-assets/login.css",
                                "/account-assets/login.js", "/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/account", "/api/v1/account",
                                "/api/v1/account/activity").authenticated()
                        .anyRequest().denyAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        // Honor saved OAuth authorization requests; standalone login has a usable destination.
                        .defaultSuccessUrl("/account", false)
                        .failureHandler((request, response, exception) -> {
                            activities.failedLogin(request.getParameter("username"));
                            if (browserLogin.matches(request)) response.sendRedirect("/login?error");
                            else response.setStatus(401);
                        }))
                .logout(logout -> logout
                        // CSRF remains enabled: GET shows confirmation, only POST performs logout.
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().equals("/actuator")
                                        || request.getServletPath().startsWith("/actuator/"))
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> !String.valueOf(request.getHeader("Accept")).contains("text/html")))
                .build();
    }
}
