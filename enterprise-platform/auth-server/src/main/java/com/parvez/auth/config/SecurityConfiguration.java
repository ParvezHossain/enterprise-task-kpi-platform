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
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator", "/actuator/**").authenticated()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/account").authenticated()
                        .anyRequest().denyAll())
                .formLogin(form -> form
                        // Honor saved OAuth authorization requests; standalone login has a usable destination.
                        .defaultSuccessUrl("/account", false)
                        .failureHandler((request, response, exception) -> response.setStatus(401)))
                .logout(logout -> logout
                        // CSRF remains enabled: GET shows confirmation, only POST performs logout.
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        // Keep the default /login?logout success URL so the generated
                        // login filter also renders its signed-out notice.
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().equals("/actuator")
                                        || request.getServletPath().startsWith("/actuator/"))
                        // A global entry point suppresses Spring's generated login page.
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> !String.valueOf(request.getHeader("Accept")).contains("text/html")))
                .build();
    }
}
