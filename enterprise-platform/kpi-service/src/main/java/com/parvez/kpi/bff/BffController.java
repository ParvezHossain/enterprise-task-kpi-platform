package com.parvez.kpi.bff;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@Profile("bff")
public class BffController {
    private final OAuth2AuthorizedClientManager manager;
    private final JwtDecoder decoder;
    private final RestClient rest;
    private final String api;

    public BffController(OAuth2AuthorizedClientManager manager, JwtDecoder decoder, @Value("${bff.api-url:http://127.0.0.1:8083}") String api) {
        this.manager = manager;
        this.decoder = decoder;
        this.api = api;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        rest = RestClient.builder().requestFactory(factory).build();
    }

    private OAuth2AuthorizedClient client(Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        var client = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("kpi-ui").principal(principal).attribute(HttpServletRequest.class.getName(), request).attribute(HttpServletResponse.class.getName(), response).build());
        if (client == null) throw new org.springframework.security.access.AccessDeniedException("Login required");
        return client;
    }

    @GetMapping("/bff/session")
    public Map<String, Object> session(Authentication principal, HttpServletRequest request, HttpServletResponse response, CsrfToken csrf) {
        var jwt = decoder.decode(client(principal, request, response).getAccessToken().getTokenValue());
        return Map.of("subject", jwt.getSubject(), "roles", Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of()), "csrfToken", csrf.getToken());
    }

    @RequestMapping(value = "/bff/api/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<String> proxy(Authentication principal, HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) String body) {
        String path = request.getRequestURI().substring(4);
        if (!path.startsWith("/api/v1/kpis"))
            throw new org.springframework.security.access.AccessDeniedException("Route denied");
        String target = api + path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        String token = client(principal, request, response).getAccessToken().getTokenValue();
        var outgoing = rest.method(HttpMethod.valueOf(request.getMethod())).uri(target).headers(h -> {
            h.setBearerAuth(token);
            h.setContentType(MediaType.APPLICATION_JSON);
            String id = org.slf4j.MDC.get("requestId");
            if (id != null) h.set("X-Request-ID", id);
            String key = request.getHeader("Idempotency-Key");
            if (key != null) h.set("Idempotency-Key", key);
        });
        if (body != null) outgoing.body(body);
        return outgoing.exchange((r, s) -> {
            var headers = new HttpHeaders();
            for (String name : List.of("Content-Type", "Location", "Retry-After", "X-Request-ID")) {
                var values = s.getHeaders().get(name);
                if (values != null) headers.put(name, values);
            }
            return new ResponseEntity<>(new String(s.getBody().readNBytes(2_000_000), java.nio.charset.StandardCharsets.UTF_8), headers, s.getStatusCode());
        });
    }
}
