-- DEVELOPMENT / TEST ONLY. Confidential BFF clients; never put secrets in browser JS.
-- Supply independently generated encoded secrets through Flyway placeholders.
INSERT INTO oauth2_registered_client
(id, client_id, client_name, client_secret, client_authentication_methods,
 authorization_grant_types, redirect_uris, scopes, client_settings, token_settings)
VALUES
('task-management-ui', 'task-management-ui', 'task-management-ui', '${taskClientSecretHash}', 'client_secret_basic',
 'authorization_code,refresh_token', 'http://127.0.0.1:8080/login/oauth2/code/task-management-ui',
 'openid,profile,email,task.read,task.write',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT5M"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT8H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT2M"]}'),
('kpi-ui', 'kpi-ui', 'kpi-ui', '${kpiClientSecretHash}', 'client_secret_basic',
 'authorization_code,refresh_token', 'http://127.0.0.1:8081/login/oauth2/code/kpi-ui',
 'openid,profile,email,kpi.read',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT5M"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT8H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT2M"]}');
