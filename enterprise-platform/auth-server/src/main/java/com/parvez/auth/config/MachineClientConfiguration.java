package com.parvez.auth.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
@Configuration(proxyBeanMethods=false)
public class MachineClientConfiguration {
    @Bean ApplicationRunner provisionMachineClient(org.flywaydb.core.Flyway flyway,@Value("${auth.kpi-sync.secret-hash:}") String secretHash) {
        return args -> {
            if(secretHash.isBlank()) return;
            if(!secretHash.startsWith("{argon2@SpringSecurity_v5_8}")) throw new IllegalArgumentException("Encoded machine client secret required");
            RegisteredClientRepository clients=new JdbcRegisteredClientRepository(new org.springframework.jdbc.core.JdbcTemplate(flyway.getConfiguration().getDataSource()));
            RegisteredClient existing=clients.findByClientId("kpi-sync");
            var client=RegisteredClient.withId(existing==null?java.util.UUID.randomUUID().toString():existing.getId()).clientId("kpi-sync").clientName("KPI metric synchronization")
                .clientSecret(secretHash).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("task.metrics.read")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()).tokenSettings(TokenSettings.builder().accessTokenTimeToLive(java.time.Duration.ofMinutes(5)).build()).build();
            clients.save(client);
        };
    }
}
