package com.builddash.backend.infra.security;

import com.builddash.backend.domain.port.RateLimiter;
import com.builddash.backend.infra.config.RateLimitProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimiter rateLimiter;

    private RateLimitProperties properties;
    // Records need the parameter-names module; the production filter gets Boot's
    // pre-configured mapper, the unit test must register modules itself.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        RateLimitProperties.Rule search = new RateLimitProperties.Rule();
        search.setPath("/search/**");
        search.setLimit(30);
        search.setWindow(Duration.ofMinutes(1));
        properties.getRules().put("search", search);
        filter = new RateLimitFilter(rateLimiter, properties, objectMapper);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void underLimit_passesThroughToChain() throws ServletException, IOException {
        when(rateLimiter.allow(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/search/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void overLimit_selfRenders429WithRateLimitedCode() throws ServletException, IOException {
        when(rateLimiter.allow(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = request("GET", "/search/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("path").asText()).isEqualTo("/search/products");
    }

    @Test
    void nonMatchingPath_neverConsultsPort() throws ServletException, IOException {
        MockHttpServletRequest request = request("GET", "/products");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(rateLimiter, never()).allow(anyString(), anyString(), anyInt(), any(Duration.class));
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void methodMismatch_skipsRule() throws ServletException, IOException {
        RateLimitProperties.Rule postOnly = new RateLimitProperties.Rule();
        postOnly.setPath("/search/**");
        postOnly.setMethod("POST");
        postOnly.setLimit(5);
        postOnly.setWindow(Duration.ofMinutes(1));
        properties.getRules().put("postOnly", postOnly);
        properties.getRules().remove("search");

        MockHttpServletRequest request = request("GET", "/search/products");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(rateLimiter, never()).allow(anyString(), anyString(), anyInt(), any(Duration.class));
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void optionsRequests_exempt() throws ServletException, IOException {
        MockHttpServletRequest request = request("OPTIONS", "/search/products");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(rateLimiter, never()).allow(anyString(), anyString(), anyInt(), any(Duration.class));
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void clientIpDerivedFromForwardedForLeftmostEntry() throws ServletException, IOException {
        when(rateLimiter.allow(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/search/products?q=x");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimiter).allow("search", "203.0.113.7", 30, Duration.ofMinutes(1));
    }

    @Test
    void ruleNameLimitAndWindow_forwardedVerbatim() throws ServletException, IOException {
        when(rateLimiter.allow(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/search");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimiter).allow("search", "127.0.0.1", 30, Duration.ofMinutes(1));
    }

    @Test
    void forwardedForFallback_usesRemoteAddr() throws ServletException, IOException {
        when(rateLimiter.allow(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/search");
        request.setRemoteAddr("192.0.2.55");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimiter).allow("search", "192.0.2.55", 30, Duration.ofMinutes(1));
    }
}
