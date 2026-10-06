package com.parvez.auth.web;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class LoginRateLimitFilterTest {
    @Test void restrictsRepeatedAddressesWithoutCrossAddressInterference() {
        var limiter=new LoginRateLimitFilter(2,60,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        assertThat(limiter.allowed("one")).isTrue();assertThat(limiter.allowed("one")).isTrue();
        assertThat(limiter.allowed("one")).isFalse();assertThat(limiter.allowed("two")).isTrue();
    }
    @Test void windowExpiresWithoutSleeping() {
        var clock=new Clock() {long value; public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochSecond(value);} };
        var limiter=new LoginRateLimitFilter(1,60,clock);
        assertThat(limiter.allowed("one")).isTrue();assertThat(limiter.allowed("one")).isFalse();
        clock.value=61;assertThat(limiter.allowed("one")).isTrue();
    }
    @Test void excessLoginReturnsProblemDetailsAndRetryAfter() throws Exception {
        var limiter=new LoginRateLimitFilter(1,60,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        var first=new org.springframework.mock.web.MockHttpServletRequest("POST","/login");first.setServletPath("/login");
        limiter.doFilter(first,new org.springframework.mock.web.MockHttpServletResponse(),new org.springframework.mock.web.MockFilterChain());
        var second=new org.springframework.mock.web.MockHttpServletRequest("POST","/login");second.setServletPath("/login");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        limiter.doFilter(second,response,new org.springframework.mock.web.MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":429","\"instance\":\"/login\"");
    }
}
