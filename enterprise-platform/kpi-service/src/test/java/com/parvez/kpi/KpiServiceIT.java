package com.parvez.kpi;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.sql.Timestamp;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.parvez.kpi.integration.KpiSynchronizer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KpiServiceIT {
    static final String ISSUER="https://auth.example.test";
    static final KeyPair KEYS=keys();
    static final HttpServer STUB=stub();
    static volatile String mode="healthy";
    static volatile boolean manages=true;
    static volatile String correlation;
    static final UUID EMPLOYEE=UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID OTHER=UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID TEAM=UUID.randomUUID();
    static final java.util.concurrent.CountDownLatch TIMEOUT=new java.util.concurrent.CountDownLatch(1);
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6");
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JdbcTemplate jdbc;
    @Autowired KpiSynchronizer sync;
    @LocalServerPort int port;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.flyway.user",POSTGRES::getUsername);p.add("spring.flyway.password",POSTGRES::getPassword);p.add("spring.flyway.placeholders.runtimeRole",POSTGRES::getUsername);
        p.add("kpi.security.issuer",()->ISSUER);p.add("kpi.security.jwk-set-uri",()->url()+"/jwks");
        p.add("kpi.task-url",KpiServiceIT::url);p.add("kpi.token-url",()->url()+"/token");p.add("kpi.sync-secret",()->UUID.randomUUID().toString());p.add("kpi.sync-enabled",()->false);
    }
    @AfterAll static void stop(){STUB.stop(0);}
    @Test @Order(1) void unavailableBeforeFirstSuccessfulRefreshAndJwtBoundaryIsEnforced() throws Exception {
        assertThat(get("/api/v1/kpis/me",token(EMPLOYEE,"EMPLOYEE","kpi-service",ISSUER)).statusCode()).isEqualTo(503);
        assertThat(get("/api/v1/kpis/me",null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/kpis/me",token(EMPLOYEE,"EMPLOYEE","task-management",ISSUER)).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/kpis/me",token(EMPLOYEE,"EMPLOYEE","kpi-service","https://wrong.test")).statusCode()).isEqualTo(401);
    }
    @Test @Order(2) void emptySuccessfulPullProducesZerosAndCorrelation() throws Exception {
        mode="healthy";assertThat(sync.refresh()).isTrue();assertThat(correlation).matches("[a-zA-Z0-9-]{1,64}");
        var response=get("/api/v1/kpis/me",token(EMPLOYEE,"EMPLOYEE","kpi-service",ISSUER));
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("\"total\":0","\"stale\":false","\"score\":0.00");
    }
    @Test @Order(3) void sqlAggregatesRankingsTrendsAndRoleBoundariesAreExact() throws Exception {
        UUID generation=jdbc.queryForObject("SELECT active_generation FROM kpi_sync_state",UUID.class);
        for(int i=0;i<10;i++) {
            boolean completed=i<6;String priority="HIGH";
            Instant created=Instant.now().minusSeconds(86400*5),started=created.plusSeconds(3600),finished=started.plusSeconds(7200);
            LocalDate due=i==5?LocalDate.now().minusDays(10):i>=8?LocalDate.now().minusDays(1):LocalDate.now();
            jdbc.update("INSERT INTO metric_tasks(generation,task_id,version,employee_id,team_id,project_id,status,priority,due_date,created_at,started_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",generation,UUID.randomUUID(),0,EMPLOYEE,TEAM,UUID.randomUUID(),completed?"COMPLETED":"IN_PROGRESS",priority,due,Timestamp.from(created),Timestamp.from(started),completed?Timestamp.from(finished):null);
        }
        var employee=token(EMPLOYEE,"EMPLOYEE","kpi-service",ISSUER);
        org.mockito.Mockito.clearInvocations(jdbc);
        var me=get("/api/v1/kpis/me",employee);assertThat(me.statusCode()).isEqualTo(200);
        long queryCount=org.mockito.Mockito.mockingDetails(jdbc).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("query")
                && invocation.getArguments()[0] instanceof String
                && invocation.getArguments()[invocation.getArguments().length-1] instanceof org.springframework.jdbc.core.ResultSetExtractor).count();
        assertThat(queryCount).isEqualTo(2); // One generation lookup, one aggregate query, independent of task count.
        var data=new ObjectMapper().readTree(me.body()).get("data");
        assertThat(data.get("counts").get("total").asLong()).isEqualTo(10);
        assertThat(data.get("counts").get("completed").asLong()).isEqualTo(6);
        assertThat(data.get("counts").get("onTime").asLong()).isEqualTo(5);
        assertThat(data.get("counts").get("overdue").asLong()).isEqualTo(2);
        assertThat(data.get("counts").get("meanCompletionHours").asDouble()).isEqualTo(2);
        assertThat(data.get("score").asDouble()).isEqualTo(58.5);
        assertThat(get("/api/v1/kpis/company",employee).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/kpis/ranking",employee).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/kpis/top-performers",employee).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/kpis/trends?scope=company",employee).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/kpis/company",token(OTHER,"PROJECT_MANAGER","kpi-service",ISSUER)).statusCode()).isEqualTo(200);
        var admin=token(OTHER,"ADMIN","kpi-service",ISSUER);
        var ranking=get("/api/v1/kpis/ranking?size=10000",admin);assertThat(ranking.statusCode()).isEqualTo(200);
        assertThat(ranking.body()).contains("\"size\":100","58.50");
        assertThat(get("/api/v1/kpis/trends?scope=company",admin).body()).contains("\"completed\":6");
        assertThat(get("/api/v1/kpis/distribution?scope=company",admin).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/kpis/trends?scope=company&size=10000",admin).body()).contains("\"size\":16");
        assertThat(get("/api/v1/kpis/trends?scope=company&page=1&size=1",admin).body()).contains("\"content\":[]");
        assertThat(get("/api/v1/kpis/distribution?scope=company&size=1",admin).body()).contains("\"hasNext\":true");
        assertThat(get("/api/v1/kpis/trends?page=-1",admin).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/kpis/me",token(OTHER,"EMPLOYEE","kpi-service",ISSUER)).body()).contains("\"total\":0");
        var leader=token(OTHER,"TEAM_LEADER","kpi-service",ISSUER);manages=true;
        assertThat(get("/api/v1/kpis/team?teamId="+TEAM,leader).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/kpis/top-performers?teamId="+TEAM,leader).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/kpis/top-performers",admin).statusCode()).isEqualTo(200);
        manages=false;assertThat(get("/api/v1/kpis/team?teamId="+TEAM,leader).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/kpis/trends?scope=team&teamId="+TEAM,leader).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/kpis/top-performers?teamId="+TEAM,leader).statusCode()).isEqualTo(403);
        jdbc.update("INSERT INTO metric_tasks(generation,task_id,version,employee_id,team_id,project_id,status,priority,created_at,completed_at) VALUES(?,?,?,?,?,?,'COMPLETED','URGENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",generation,UUID.randomUUID(),0,OTHER,TEAM,UUID.randomUUID());
        var urgent=new ObjectMapper().readTree(get("/api/v1/kpis/me",token(OTHER,"EMPLOYEE","kpi-service",ISSUER)).body()).get("data");
        assertThat(urgent.get("counts").get("meanPriorityWeight").asDouble()).isEqualTo(4);
        assertThat(urgent.get("score").asDouble()).isEqualTo(51);
        assertThat(get("/api/v1/kpis/ranking?page=-1",admin).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/kpis/me?from=2020-01-01&to=2025-01-01",employee).statusCode()).isEqualTo(400);
    }
    @Test @Order(4) void failedOrPartialSweepPreservesLastCompleteGenerationAndLeasePreventsOverlap() {
        UUID previous=jdbc.queryForObject("SELECT active_generation FROM kpi_sync_state",UUID.class);
        mode="failure";assertThat(sync.refresh()).isFalse();
        assertThat(jdbc.queryForObject("SELECT active_generation FROM kpi_sync_state",UUID.class)).isEqualTo(previous);
        mode="partial";assertThat(sync.refresh()).isFalse();
        mode="timeout";assertThat(sync.refresh()).isFalse();
        assertThat(jdbc.queryForObject("SELECT active_generation FROM kpi_sync_state",UUID.class)).isEqualTo(previous);
        jdbc.update("UPDATE kpi_sync_state SET lease_owner=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '1 minute'",UUID.randomUUID());
        assertThat(sync.refresh()).isFalse();
        jdbc.update("UPDATE kpi_sync_state SET lease_owner=NULL,lease_until=NULL");
    }
    @Test @Order(5) void metricsAndHeadersAreAvailableToAuthenticatedUsers() throws Exception {
        String admin=token(OTHER,"ADMIN","kpi-service",ISSUER);
        assertThat(get("/api/v1/kpis/no-such-route",admin).statusCode()).isEqualTo(404);
        var unsupported=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/kpis/me"))
            .header("Authorization","Bearer "+admin).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(HttpClient.newHttpClient().send(unsupported,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        var response=get("/actuator/prometheus",admin);
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("kpi_sync_success_total","kpi_sync_failure_total");
        assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
    }
    HttpResponse<String> get(String path,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    static String token(UUID user,String role,String audience,String issuer) {
        var key=new RSAKey.Builder((RSAPublicKey)KEYS.getPublic()).privateKey((RSAPrivateKey)KEYS.getPrivate()).keyID("test").build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(),JwtClaimsSet.builder().issuer(issuer).subject(user.toString()).audience(List.of(audience)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("roles",List.of(role)).build())).getTokenValue();
    }
    static KeyPair keys(){try{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static String url(){return "http://127.0.0.1:"+STUB.getAddress().getPort();}
    static HttpServer stub() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath();String body;int status=200;
                if(path.equals("/jwks"))body=new JWKSet(new RSAKey.Builder((RSAPublicKey)KEYS.getPublic()).keyID("test").build()).toString();
                else if(path.equals("/token"))body="{\"access_token\":\"stub-machine-token\",\"expires_in\":300}";
                else if(path.endsWith("team-access"))body="{\"allowed\":"+manages+"}";
                else {
                    if(mode.equals("timeout")) {
                        try { TIMEOUT.await(6,java.util.concurrent.TimeUnit.SECONDS); }
                        catch(InterruptedException error) {Thread.currentThread().interrupt();}
                    }
                    correlation=exchange.getRequestHeaders().getFirst("X-Request-ID");
                    if(mode.equals("failure") || (mode.equals("partial")&&exchange.getRequestURI().getQuery().contains("cursor="))){status=503;body="{}";}
                    else if(mode.equals("partial")) body=new ObjectMapper().writeValueAsString(Map.of("content",List.of(Map.of("id","11111111-1111-1111-1111-111111111111","version",0,"teamId",TEAM.toString(),"projectId",TEAM.toString(),"status","DRAFT","priority","HIGH","createdAt",Instant.now().toString())),"upperId","11111111-1111-1111-1111-111111111111","nextCursor","11111111-1111-1111-1111-111111111111"));
                    else body="{\"content\":[],\"upperId\":null,\"nextCursor\":null}";
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
}
