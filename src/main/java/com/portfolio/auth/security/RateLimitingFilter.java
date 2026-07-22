package com.portfolio.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-IP token-bucket rate limiter for the credential endpoints (/api/auth/**).
 *
 * This blunts credential-stuffing and brute-force attempts. A single-instance in-memory store is
 * fine for a demo; a horizontally-scaled deployment would back this with a shared store such as
 * Redis so limits are enforced across instances.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final int capacity;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(@Value("${ratelimit.capacity}") int capacity,
                              @Value("${ratelimit.window-seconds}") long windowSeconds) {
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, windowSeconds));

        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded, try again later\"}");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Classic token bucket with lazy, time-proportional refill. */
    private static final class Bucket {
        private final int capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, long windowSeconds) {
            this.capacity = capacity;
            this.refillPerNano = (double) capacity / (windowSeconds * 1_000_000_000.0);
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double replenished = (now - lastRefillNanos) * refillPerNano;
            if (replenished > 0) {
                tokens = Math.min(capacity, tokens + replenished);
                lastRefillNanos = now;
            }
        }
    }
}
