package com.yongsoo.youtubeatlasbackend.common;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String UNKNOWN_CLIENT_IP = "unknown";

    private final AtlasProperties atlasProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Cache<String, TokenBucket> buckets;

    public RateLimitFilter(AtlasProperties atlasProperties, ObjectMapper objectMapper, Clock clock) {
        this.atlasProperties = atlasProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(Math.max(1L, atlasProperties.getRateLimit().getMaxTrackedClients()))
            .build();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitPolicy policy = resolvePolicy(request);
        if (policy == null || !atlasProperties.getRateLimit().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String bucketKey = policy.name() + ":" + clientIp;
        TokenBucket bucket = buckets.get(bucketKey, ignored -> TokenBucket.create(policy.requestsPerMinute(), nowMillis()));
        RateLimitDecision decision = bucket.tryConsume(nowMillis());

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRateLimitResponse(response, decision.retryAfterSeconds());
    }

    private RateLimitPolicy resolvePolicy(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        AtlasProperties.RateLimit rateLimit = atlasProperties.getRateLimit();

        if ("OPTIONS".equalsIgnoreCase(method) || path == null || !path.startsWith("/api/")) {
            return null;
        }

        if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
            return null;
        }

        if ("POST".equalsIgnoreCase(method) && "/api/trending/sync".equals(path)) {
            return new RateLimitPolicy("sensitive", rateLimit.getSensitivePerMinute());
        }

        if ("POST".equalsIgnoreCase(method) && "/api/auth/google".equals(path)) {
            return new RateLimitPolicy("login", rateLimit.getLoginPerMinute());
        }

        if ("POST".equalsIgnoreCase(method) && ("/api/comments".equals(path) || isVideoCommentPath(path))) {
            return new RateLimitPolicy("comment", rateLimit.getCommentPerMinute());
        }

        if ("POST".equalsIgnoreCase(method) && isTradePreviewPath(path)) {
            return new RateLimitPolicy("preview", rateLimit.getPreviewPerMinute());
        }

        if ("POST".equalsIgnoreCase(method) && isTradePath(path)) {
            return new RateLimitPolicy("trade", rateLimit.getTradePerMinute());
        }

        return new RateLimitPolicy("general", rateLimit.getGeneralPerMinute());
    }

    private boolean isVideoCommentPath(String path) {
        return path.startsWith("/api/videos/") && path.endsWith("/comments");
    }

    private boolean isTradePath(String path) {
        return "/api/game/positions".equals(path)
            || "/api/game/positions/sell".equals(path)
            || (path.startsWith("/api/game/positions/") && path.endsWith("/sell"));
    }

    private boolean isTradePreviewPath(String path) {
        return "/api/game/positions/sell-preview".equals(path);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (atlasProperties.getRateLimit().isTrustForwardedHeaders()) {
            String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String firstForwardedIp = forwardedFor.split(",", 2)[0].trim();
                if (!firstForwardedIp.isBlank()) {
                    return firstForwardedIp;
                }
            }

            String realIp = request.getHeader(REAL_IP_HEADER);
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? UNKNOWN_CLIENT_IP : remoteAddr;
    }

    private long nowMillis() {
        return clock.millis();
    }

    private void writeRateLimitResponse(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiErrorResponse(
                "rate_limit_exceeded",
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                retryAfterSeconds
            )
        );
    }

    private record RateLimitPolicy(String name, int requestsPerMinute) {
    }

    private record RateLimitDecision(boolean allowed, int retryAfterSeconds) {
    }

    private static final class TokenBucket {

        private final double capacity;
        private final double refillPerMillis;
        private double tokens;
        private long lastRefillAtMillis;

        private TokenBucket(int requestsPerMinute, long nowMillis) {
            int normalizedLimit = Math.max(1, requestsPerMinute);
            this.capacity = normalizedLimit;
            this.refillPerMillis = normalizedLimit / 60_000D;
            this.tokens = normalizedLimit;
            this.lastRefillAtMillis = nowMillis;
        }

        static TokenBucket create(int requestsPerMinute, long nowMillis) {
            return new TokenBucket(requestsPerMinute, nowMillis);
        }

        synchronized RateLimitDecision tryConsume(long nowMillis) {
            refill(nowMillis);
            if (tokens >= 1D) {
                tokens -= 1D;
                return new RateLimitDecision(true, 0);
            }

            double missingTokens = 1D - tokens;
            int retryAfterSeconds = Math.max(1, (int) Math.ceil(missingTokens / refillPerMillis / 1_000D));
            return new RateLimitDecision(false, retryAfterSeconds);
        }

        private void refill(long nowMillis) {
            if (nowMillis <= lastRefillAtMillis) {
                return;
            }

            long elapsedMillis = nowMillis - lastRefillAtMillis;
            tokens = Math.min(capacity, tokens + elapsedMillis * refillPerMillis);
            lastRefillAtMillis = nowMillis;
        }
    }
}
