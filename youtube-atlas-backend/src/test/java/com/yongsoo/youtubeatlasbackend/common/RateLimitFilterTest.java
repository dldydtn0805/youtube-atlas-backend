package com.yongsoo.youtubeatlasbackend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private AtlasProperties atlasProperties;
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        atlasProperties = new AtlasProperties();
        atlasProperties.getRateLimit().setGeneralPerMinute(1);
        atlasProperties.getRateLimit().setLoginPerMinute(1);
        atlasProperties.getRateLimit().setCommentPerMinute(1);
        atlasProperties.getRateLimit().setTradePerMinute(1);
        atlasProperties.getRateLimit().setSensitivePerMinute(1);
        rateLimitFilter = new RateLimitFilter(
            atlasProperties,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-04-19T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rateLimitsApiRequestsByClientIp() throws Exception {
        MockHttpServletResponse firstResponse = doRequest("GET", "/api/comments", "203.0.113.10");
        MockHttpServletResponse secondResponse = doRequest("GET", "/api/comments", "203.0.113.10");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(secondResponse.getContentAsString()).contains("rate_limit_exceeded");
    }

    @Test
    void usesFirstForwardedForIpWhenPresent() throws Exception {
        atlasProperties.getRateLimit().setTrustForwardedHeaders(true);

        MockHttpServletRequest firstRequest = request("POST", "/api/auth/google", "198.51.100.1");
        firstRequest.addHeader("X-Forwarded-For", "203.0.113.20, 198.51.100.99");
        MockHttpServletResponse firstResponse = doRequest(firstRequest);

        MockHttpServletRequest secondRequest = request("POST", "/api/auth/google", "198.51.100.2");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.20, 198.51.100.88");
        MockHttpServletResponse secondResponse = doRequest(secondRequest);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresForwardedForByDefault() throws Exception {
        MockHttpServletRequest firstRequest = request("POST", "/api/auth/google", "198.51.100.1");
        firstRequest.addHeader("X-Forwarded-For", "203.0.113.20");
        MockHttpServletResponse firstResponse = doRequest(firstRequest);

        MockHttpServletRequest secondRequest = request("POST", "/api/auth/google", "198.51.100.2");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.20");
        MockHttpServletResponse secondResponse = doRequest(secondRequest);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsOptionsRequests() throws Exception {
        MockHttpServletResponse firstResponse = doRequest("OPTIONS", "/api/comments", "203.0.113.30");
        MockHttpServletResponse secondResponse = doRequest("OPTIONS", "/api/comments", "203.0.113.30");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsNonApiRequests() throws Exception {
        MockHttpServletResponse firstResponse = doRequest("GET", "/actuator/health", "203.0.113.40");
        MockHttpServletResponse secondResponse = doRequest("GET", "/actuator/health", "203.0.113.40");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsAdminRequests() throws Exception {
        MockHttpServletResponse firstResponse = doRequest("GET", "/api/admin/dashboard", "203.0.113.45");
        MockHttpServletResponse secondResponse = doRequest("GET", "/api/admin/dashboard", "203.0.113.45");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void canBeDisabledByConfiguration() throws Exception {
        atlasProperties.getRateLimit().setEnabled(false);

        MockHttpServletResponse firstResponse = doRequest("GET", "/api/comments", "203.0.113.50");
        MockHttpServletResponse secondResponse = doRequest("GET", "/api/comments", "203.0.113.50");

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse doRequest(String method, String path, String remoteAddr) throws Exception {
        return doRequest(request(method, path, remoteAddr));
    }

    private MockHttpServletResponse doRequest(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
