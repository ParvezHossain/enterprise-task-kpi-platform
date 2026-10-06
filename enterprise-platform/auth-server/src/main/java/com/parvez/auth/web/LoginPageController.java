package com.parvez.auth.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Presentation only; Spring Security continues to process both POST forms. */
@RestController
public class LoginPageController {
    private final String loginTemplate;
    private final String logoutTemplate;

    public LoginPageController() throws IOException {
        loginTemplate = new ClassPathResource("account/login.html").getContentAsString(StandardCharsets.UTF_8);
        logoutTemplate = new ClassPathResource("account/logout.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(HttpServletRequest request, CsrfToken csrf) {
        // Only flag presence is used. Never echo submitted credentials or query values.
        return form(loginTemplate, csrf)
                .replace("{{errorHidden}}", request.getParameterMap().containsKey("error") ? "" : "hidden")
                .replace("{{logoutHidden}}", request.getParameterMap().containsKey("logout") ? "" : "hidden");
    }

    @GetMapping(value = "/logout", produces = MediaType.TEXT_HTML_VALUE)
    public String logout(CsrfToken csrf) {
        return form(logoutTemplate, csrf);
    }

    private String form(String template, CsrfToken csrf) {
        return template.replace("{{csrfName}}", HtmlUtils.htmlEscape(csrf.getParameterName()))
                .replace("{{csrfToken}}", HtmlUtils.htmlEscape(csrf.getToken()));
    }
}
