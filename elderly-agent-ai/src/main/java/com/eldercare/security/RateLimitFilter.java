package com.eldercare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Simple fixed-window rate limiter backed by Redis.
 * Protects /api/auth/login and /api/chat from brute force / abuse.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final long chatLimit;
    private final long loginLimit;

    public RateLimitFilter(StringRedisTemplate redis,
                           @Value("${rate-limit.chat-per-minute}") long chatLimit,
                           @Value("${rate-limit.login-per-minute}") long loginLimit) {
        this.redis = redis;
        this.chatLimit = chatLimit;
        this.loginLimit = loginLimit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !(p.equals("/api/auth/login") || p.equals("/api/chat"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = request.getRequestURI().equals("/api/auth/login") ? loginLimit : chatLimit;
        String client = clientKey(request);
        String bucketKey = "rl:" + request.getRequestURI() + ":" + client;

        Long count = redis.opsForValue().increment(bucketKey);
        if (count != null && count == 1) {
            redis.expire(bucketKey, Duration.ofMinutes(1));
        }
        if (count != null && count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please wait a moment.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
