package com.parvez.task.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthenticatedUserController {
    @GetMapping("/whoami")
    AuthenticatedUserResponse whoami(@AuthenticationPrincipal Jwt jwt) {
        return new AuthenticatedUserResponse(jwt.getSubject());
    }

    record AuthenticatedUserResponse(String subject) {
    }
}