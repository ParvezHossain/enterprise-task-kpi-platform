package com.parvez.auth.config;

import java.security.interfaces.RSAPrivateCrtKey;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SigningProperties.class)
public class SigningConfiguration {
    @Bean
    public JWKSource<SecurityContext> jwkSource(SigningProperties properties) {
        try (var privateInput = properties.privateKey().getInputStream();
             var publicInput = properties.publicKey().getInputStream()) {
            var privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            var publicKey = RsaKeyConverters.x509().convert(publicInput);
            if (!(privateKey instanceof RSAPrivateCrtKey crt) || publicKey == null
                    || publicKey.getModulus().bitLength() < 2048
                    || !crt.getModulus().equals(publicKey.getModulus())
                    || !crt.getPublicExponent().equals(publicKey.getPublicExponent())) {
                throw new IllegalArgumentException();
            }
            var key = new RSAKey.Builder(publicKey).privateKey(privateKey)
                    .keyID(properties.keyId()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
            return new ImmutableJWKSet<>(new JWKSet(key));
        } catch (Exception exception) {
            // Never include PEM contents or parser causes in startup logs.
            throw new IllegalStateException("Configure a matching RSA key pair of at least 2048 bits (PKCS8 private/X509 public PEM)");
        }
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(SigningProperties properties) {
        var issuer = properties.issuer();
        boolean loopback = "localhost".equals(issuer.getHost()) || "127.0.0.1".equals(issuer.getHost());
        if (issuer.getHost() == null || issuer.getQuery() != null || issuer.getFragment() != null
                || issuer.getUserInfo() != null || !("https".equals(issuer.getScheme())
                || ("http".equals(issuer.getScheme()) && loopback))) {
            throw new IllegalStateException("Configure an HTTPS issuer (HTTP is allowed only on loopback for development)");
        }
        return AuthorizationServerSettings.builder().issuer(issuer.toString()).build();
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> keys) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(keys);
    }
}
