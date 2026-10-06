package com.parvez.auth.web;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final Map<String,Window> attempts=new LinkedHashMap<>();
    private final int limit; private final long interval; private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public LoginRateLimitFilter(@Value("${auth.login-rate-limit.attempts:20}") int limit,@Value("${auth.login-rate-limit.window-seconds:60}") long seconds) {this(limit,seconds,Clock.systemUTC());}
    public LoginRateLimitFilter(int limit,long seconds,Clock clock) {
        if(limit<1||seconds<1) throw new IllegalArgumentException("Positive login limits required");this.limit=limit;this.interval=seconds*1000;this.clock=clock;
    }
    public synchronized boolean allowed(String address) {
        long now=clock.millis();attempts.entrySet().removeIf(e -> now-e.getValue().start>=interval);
        Window window=attempts.get(address);
        if(window==null) { if(attempts.size()>=10000) return false;window=new Window(now);attempts.put(address,window); }
        return ++window.count<=limit;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        if("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()) && !allowed(request.getRemoteAddr())) {
            response.setStatus(429);response.setHeader("Retry-After",Long.toString(interval/1000));response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Please retry later.\",\"instance\":\"/login\"}");return;
        }
        chain.doFilter(request,response);
    }
    private static final class Window {final long start;int count;Window(long start){this.start=start;}}
}
