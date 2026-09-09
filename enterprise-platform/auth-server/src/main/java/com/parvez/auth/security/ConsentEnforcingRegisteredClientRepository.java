package com.parvez.auth.security;

import java.util.Set;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/** Only reserved, operator-owned first-party clients may opt out of consent. */
public final class ConsentEnforcingRegisteredClientRepository implements RegisteredClientRepository {
    private static final Set<String> FIRST_PARTY_CLIENTS = Set.of("task-management-ui", "kpi-ui");
    private final RegisteredClientRepository delegate;

    public ConsentEnforcingRegisteredClientRepository(RegisteredClientRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void save(RegisteredClient client) {
        delegate.save(enforceConsent(client));
    }

    @Override
    public RegisteredClient findById(String id) {
        return enforceConsent(delegate.findById(id));
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return enforceConsent(delegate.findByClientId(clientId));
    }

    private RegisteredClient enforceConsent(RegisteredClient client) {
        if (client == null || FIRST_PARTY_CLIENTS.contains(client.getClientId())
                || client.getClientSettings().isRequireAuthorizationConsent()) {
            return client;
        }
        // Preserve every other setting, including PKCE and any future client restrictions.
        var settings = ClientSettings.withSettings(client.getClientSettings().getSettings())
                .requireAuthorizationConsent(true).build();
        return RegisteredClient.from(client).clientSettings(settings).build();
    }
}
