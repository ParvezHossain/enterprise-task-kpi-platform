package com.parvez.kpi.integration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component
public class TaskClient {
    private final RestClient rest;private final String taskUrl,tokenUrl,secret;
    private String token;private Instant expiry=Instant.EPOCH;
    public TaskClient(@Value("${kpi.task-url}") String taskUrl,@Value("${kpi.token-url}") String tokenUrl,@Value("${kpi.sync-secret}") String secret) {
        this.taskUrl=taskUrl;this.tokenUrl=tokenUrl;this.secret=secret;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(5));
        rest=RestClient.builder().requestFactory(factory).build();
    }
    private synchronized String token() {
        if(token!=null && Instant.now().isBefore(expiry)) return token;
        var form=new org.springframework.util.LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("scope","task.metrics.read");
        Token result=rest.post().uri(tokenUrl).headers(h->h.setBasicAuth("kpi-sync",secret)).body(form).retrieve().body(Token.class);
        if(result==null||result.access_token()==null||result.expires_in()<1) throw new IllegalStateException("Token unavailable");
        token=result.access_token();expiry=Instant.now().plusSeconds(Math.max(1,result.expires_in()-30));return token;
    }
    public record Token(String access_token,long expires_in) {}
    public record MetricTask(UUID id,long version,UUID employeeId,UUID teamId,UUID projectId,String status,String priority,LocalDate dueDate,Instant createdAt,Instant startedAt,Instant completedAt,Instant closedAt,String creationRequestId) {}
    public record Page(List<MetricTask> content,UUID upperId,UUID nextCursor) {}
    public Page page(UUID cursor,UUID upperId) {
        return rest.get().uri(org.springframework.web.util.UriComponentsBuilder.fromUriString(taskUrl+"/internal/metrics/tasks").queryParam("size",500).queryParamIfPresent("cursor",Optional.ofNullable(cursor)).queryParamIfPresent("upperId",Optional.ofNullable(upperId)).build().toUri())
                .headers(h->{h.setBearerAuth(token());h.set("X-Request-ID",correlation());}).retrieve().body(Page.class);
    }
    public boolean manages(UUID user,UUID team) {
        Access access=rest.get().uri(taskUrl+"/internal/metrics/team-access?userId="+user+"&teamId="+team)
            .headers(h->{h.setBearerAuth(token());h.set("X-Request-ID",correlation());}).retrieve().body(Access.class);
        return access!=null&&access.allowed();
    }
    private String correlation() {String id=MDC.get("requestId");return id==null?UUID.randomUUID().toString():id;}
    public record Access(boolean allowed) {}
}
