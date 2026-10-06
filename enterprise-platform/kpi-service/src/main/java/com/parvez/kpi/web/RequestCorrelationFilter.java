package com.parvez.kpi.web;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String supplied=request.getHeader("X-Request-ID");
        String id=supplied!=null && supplied.matches("[a-zA-Z0-9-]{1,64}") ? supplied : UUID.randomUUID().toString();
        var previous=MDC.getCopyOfContextMap();long start=System.nanoTime();
        MDC.put("requestId",id);MDC.put("traceId",id);response.setHeader("X-Request-ID",id);
        try { chain.doFilter(request,response); }
        finally {
            LoggerFactory.getLogger(RequestCorrelationFilter.class).info("request_completed status={} durationMs={}",response.getStatus(),(System.nanoTime()-start)/1_000_000);
            if(previous==null) MDC.clear();else MDC.setContextMap(previous);
        }
    }
}
