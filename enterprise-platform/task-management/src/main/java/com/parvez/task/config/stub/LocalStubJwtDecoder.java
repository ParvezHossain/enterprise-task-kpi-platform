package com.parvez.task.config.stub;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public final class LocalStubJwtDecoder implements JwtDecoder {
    private static final String STUB_ISSUER = "local-stub";
    private final String audience;

    public LocalStubJwtDecoder(String audience) {
        this.audience = audience;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            PlainJWT plainJwt = PlainJWT.parse(token);
            JWTClaimsSet claims = plainJwt.getJWTClaimsSet();
            Instant now = Instant.now();
            Date issuedAtValue = claims.getIssueTime();
            Date expiresAtValue = claims.getExpirationTime();
            Date notBeforeValue = claims.getNotBeforeTime();
            if (!"none".equals(plainJwt.getHeader().getAlgorithm().getName())
                    || !STUB_ISSUER.equals(claims.getIssuer())
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || !claims.getAudience().contains(audience)
                    || issuedAtValue == null || expiresAtValue == null
                    || !expiresAtValue.toInstant().isAfter(now)
                    || !expiresAtValue.toInstant().isAfter(issuedAtValue.toInstant())
                    || issuedAtValue.toInstant().isAfter(now.plusSeconds(60))
                    || (notBeforeValue != null && notBeforeValue.toInstant().isAfter(now))) {
                throw new JwtException("Invalid local-stub JWT");
            }

            Instant issuedAt = issuedAtValue.toInstant();
            Instant expiresAt = expiresAtValue.toInstant();
            Map<String, Object> mappedClaims = new HashMap<>(claims.getClaims());
            mappedClaims.put("iat", issuedAt);
            mappedClaims.put("exp", expiresAt);
            if (notBeforeValue != null) {
                mappedClaims.put("nbf", notBeforeValue.toInstant());
            }
            return new Jwt(token, issuedAt, expiresAt, Map.of("alg", "none", "typ", "JWT"), mappedClaims);
        } catch (ParseException | IllegalArgumentException exception) {
            throw new JwtException("Invalid local-stub JWT", exception);
        }
    }
}