package com.parvez.auth.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;

/** Retains Spring's storage/mappers while bounding its non-primary-key token lookup. */
public final class BoundedJdbcAuthorizationService extends JdbcOAuth2AuthorizationService {
    private static final Map<String, String> COLUMNS;
    static {
        var columns = new LinkedHashMap<String, String>();
        columns.put("state", "state");
        columns.put("code", "authorization_code_value");
        columns.put("access_token", "access_token_value");
        columns.put("refresh_token", "refresh_token_value");
        columns.put("id_token", "oidc_id_token_value");
        columns.put("user_code", "user_code_value");
        columns.put("device_code", "device_code_value");
        COLUMNS = Collections.unmodifiableMap(columns);
    }

    public BoundedJdbcAuthorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
        super(jdbc, clients);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "Token is required");
        var columns = tokenType == null ? COLUMNS.values()
                : Collections.singletonList(COLUMNS.get(tokenType.getValue()));
        if (columns.contains(null)) return null;
        String predicate = columns.stream().map(column -> column + " = ?").collect(Collectors.joining(" OR "));
        var values = Collections.nCopies(columns.size(), token).toArray();
        var results = getJdbcOperations().query("SELECT * FROM oauth2_authorization WHERE " + predicate + " LIMIT 2",
                getAuthorizationRowMapper(), values);
        if (results.size() > 1) throw new IllegalStateException("Ambiguous authorization lookup");
        return results.isEmpty() ? null : results.getFirst();
    }
}
