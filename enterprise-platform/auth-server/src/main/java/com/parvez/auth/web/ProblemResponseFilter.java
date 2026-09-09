package com.parvez.auth.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

/** Covers errors originating outside MVC as well as framework OAuth error writers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ProblemResponseFilter extends OncePerRequestFilter {
    private static final Set<String> OAUTH_ERRORS = Set.of("invalid_request", "invalid_client",
            "invalid_grant", "unauthorized_client", "unsupported_grant_type", "invalid_scope",
            "invalid_token", "insufficient_scope", "access_denied", "server_error",
            "temporarily_unavailable", "unsupported_token_type", "invalid_target",
            "unsupported_response_type", "invalid_redirect_uri", "invalid_dpop_proof",
            "interaction_required", "login_required", "account_selection_required", "consent_required",
            "invalid_request_uri", "invalid_request_object", "request_not_supported",
            "request_uri_not_supported", "registration_not_supported");
    private final ObjectMapper mapper;

    public ProblemResponseFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var wrapped = new ContentCachingResponseWrapper(response) {
            @Override public void sendError(int status) { setStatus(status); }
            @Override public void sendError(int status, String message) { setStatus(status); }
        };
        try {
            chain.doFilter(request, wrapped);
        } catch (Exception exception) {
            // Do not log framework exception text: it may contain credentials or request bodies.
            wrapped.resetBuffer();
            wrapped.setStatus(500);
        }
        int status = wrapped.getStatus();
        if (status >= 400) {
            String oauthError = null;
            try {
                var body = mapper.readTree(wrapped.getContentAsByteArray());
                if (body != null && body.has("error") && OAUTH_ERRORS.contains(body.get("error").asString())) {
                    oauthError = body.get("error").asString();
                }
            } catch (RuntimeException ignored) {
                // HTML, empty responses, and malformed error bodies use the same safe contract.
            }
            String challenge = wrapped.getHeader("WWW-Authenticate");
            if (challenge != null && challenge.regionMatches(true, 0, "Bearer", 0, 6)) {
                // Bearer handlers can put decoder exception messages in error_description.
                // Preserve the challenge/code, never the exception-derived description or URI.
                var match = java.util.regex.Pattern.compile("error=\"([a-z_]+)\"").matcher(challenge);
                String code = match.find() && OAUTH_ERRORS.contains(match.group(1)) ? match.group(1) : null;
                wrapped.setHeader("WWW-Authenticate", code == null ? "Bearer" : "Bearer error=\"" + code + "\"");
                if (oauthError == null) oauthError = code;
            }
            var problem = ProblemResponses.create(status, request.getRequestURI());
            var body = new LinkedHashMap<String, Object>();
            body.put("type", problem.getType().toString());
            body.put("title", problem.getTitle());
            body.put("status", status);
            body.put("detail", problem.getDetail());
            body.put("instance", problem.getInstance().toString());
            if (oauthError != null) body.put("error", oauthError);
            wrapped.resetBuffer();
            wrapped.setHeader("Location", null);
            wrapped.setHeader("Cache-Control", "no-store");
            wrapped.setHeader("Pragma", "no-cache");
            wrapped.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            byte[] bytes = mapper.writeValueAsBytes(body);
            wrapped.setContentLength(bytes.length);
            if (!"HEAD".equals(request.getMethod())) wrapped.getOutputStream().write(bytes);
        }
        wrapped.copyBodyToResponse();
    }
}
