package com.parvez.auth.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.parvez.auth.security.IdentityAuthenticationService;
import com.parvez.auth.service.AuthActivityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal account portal. User identity is always derived from the authenticated session.
 */
@RestController
public class AccountController {
    private final IdentityAuthenticationService identities;
    private final AuthActivityService activities;
    private final URI taskUrl;
    private final URI kpiUrl;

    public AccountController(
            IdentityAuthenticationService identities,
            AuthActivityService activities,
            @Value("${auth.portal.task-url}") URI taskUrl,
            @Value("${auth.portal.kpi-url}") URI kpiUrl) {
        this.identities = identities;
        this.activities = activities;
        this.taskUrl = applicationUrl(taskUrl);
        this.kpiUrl = applicationUrl(kpiUrl);
    }

    private static URI applicationUrl(URI url) {
        boolean loopback = "127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost());
        if (url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null
                || url.getFragment() != null || !("https".equals(url.getScheme())
                || ("http".equals(url.getScheme()) && loopback)))
            throw new IllegalArgumentException("Configure HTTPS application links, or loopback HTTP for development");
        return url;
    }

    @GetMapping("/")
    public ResponseEntity<Void> home() {
        return ResponseEntity.status(302).location(URI.create("/account")).build();
    }

    @GetMapping(value = "/account", produces = MediaType.TEXT_HTML_VALUE)
    public Resource account() {
        return new ClassPathResource("account/index.html");
    }

    public record AccountResponse(String subject, String email, List<String> roles,
                                  String csrfToken, String csrfParameterName, URI taskUrl, URI kpiUrl) {
    }

    @GetMapping("/api/v1/account")
    public AccountResponse current(Authentication authentication, CsrfToken csrf) {
        var identity = identities.identity(UUID.fromString(authentication.getName()));
        return new AccountResponse(identity.subject(), identity.email(), identity.roles(), csrf.getToken(),
                csrf.getParameterName(), taskUrl, kpiUrl);
    }

    @GetMapping("/api/v1/account/activity")
    public AuthActivityService.Page activity(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID user = UUID.fromString(authentication.getName());
        identities.identity(user); // Reject disabled/deleted identities even if their session still exists.
        return activities.history(user, page, size);
    }
}
