package com.parvez.task.bff;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BffRefreshTest {
    @Test void expiredAccessTokenRefreshesThroughTokenEndpointAndPersistsOnlyOnServer() throws Exception {
        var payload=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/oauth2/token",exchange->{
            payload.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response="{\"access_token\":\"refreshed-access\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"openid profile email\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            String provider="http://127.0.0.1:"+server.getAddress().getPort();
            var configuration=new BffConfiguration();
            var registrations=configuration.browserClients(provider,provider,"test-confidential-secret","http://127.0.0.1:8080/login/oauth2/code/task-management-ui");
            var registration=registrations.findByRegistrationId("task-management-ui");
            var repository=mock(OAuth2AuthorizedClientRepository.class);
            var principal=new UsernamePasswordAuthenticationToken("employee",null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
            var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
            var expired=new OAuth2AuthorizedClient(registration,"employee",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"expired-access",Instant.now().minusSeconds(600),Instant.now().minusSeconds(60),Set.of("openid","profile","email")),
                new OAuth2RefreshToken("test-refresh",Instant.now().minusSeconds(600)));
            when(repository.loadAuthorizedClient("task-management-ui",principal,request)).thenReturn(expired);
            var manager=configuration.clientManager(registrations,repository);
            var refreshed=manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("task-management-ui").principal(principal)
                .attribute(jakarta.servlet.http.HttpServletRequest.class.getName(),request)
                .attribute(jakarta.servlet.http.HttpServletResponse.class.getName(),response).build());
            assertThat(payload.get()).contains("grant_type=refresh_token","refresh_token=test-refresh");
            assertThat(refreshed.getAccessToken().getTokenValue()).isEqualTo("refreshed-access");
            verify(repository).saveAuthorizedClient(refreshed,principal,request,response);
            assertThat(response.getContentAsString()).doesNotContain("refreshed-access","test-refresh","test-confidential-secret");
        } finally {server.stop(0);}
    }
}
