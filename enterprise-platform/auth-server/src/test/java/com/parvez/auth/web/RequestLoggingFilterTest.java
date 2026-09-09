package com.parvez.auth.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {
    @Test
    void correlatesRequestsAndRestoresContextEvenOnFailure() throws Exception {
        var filter = new RequestLoggingFilter();
        String[] ids = new String[2];
        MDC.put("existing", "context");
        try {
            for (int i = 0; i < 2; i++) {
                int index = i;
                var response = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest(), response, (request, result) -> {
                    ids[index] = MDC.get("requestId");
                    assertThat(MDC.get("traceId")).matches("[0-9a-f]{32}");
                    assertThat(response.getHeader("X-Request-ID")).isEqualTo(ids[index]);
                });
                assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("existing");
            }
            assertThat(ids[0]).isNotEqualTo(ids[1]);
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), (request, response) -> {
                        throw new ServletException("test failure");
                    })).isInstanceOf(ServletException.class);
            assertThat(MDC.getCopyOfContextMap()).containsOnlyKeys("existing");
        } finally {
            MDC.clear();
        }
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {});
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
