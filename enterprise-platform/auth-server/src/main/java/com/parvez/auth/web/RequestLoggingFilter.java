package com.parvez.auth.web;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Request-local correlation; untrusted headers cannot supply log field values. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var previous = MDC.getCopyOfContextMap();
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("traceId", UUID.randomUUID().toString().replace("-", ""));
        response.setHeader("X-Request-ID", requestId);
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            try {
                // Never log raw paths, queries, headers, identities, bodies or exception messages.
                LOG.atInfo().addKeyValue("status", failed ? 500 : response.getStatus())
                        .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                        .log("HTTP request completed");
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        }
    }
}
