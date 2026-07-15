package com.readshelf.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Per-user (fallback: per-IP) rate limiter using a Redis sorted-set sliding window.
 *
 * NOT a @Component: it's hand-wired into the Spring Security chain AFTER JwtAuthFilter
 * (see SecurityConfig), because it needs the authenticated user — which only exists once
 * JwtAuthFilter has populated the SecurityContext. @Component would double-register it.
 *
 * Algorithm (sliding window log, "fair" semantic — only ALLOWED requests are recorded):
 *   key = ratelimit:<userId|ip>, ZSET of {member -> score(=timestamp ms)}
 *   1. evict entries older than (now - window)          -> ZREMRANGEBYSCORE
 *   2. count what remains                                -> ZCARD
 *   3. if count >= limit -> reject 429 (do NOT record)
 *   4. else record this request (ZADD) and continue
 *   5. refresh the key's TTL so idle keys disappear      -> EXPIRE
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int MAX_REQUESTS = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redis;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String key = KEY_PREFIX + resolveClientId(request);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW.toMillis();

        // Fail-open: the rate limiter protects against abuse, it must not itself become a
        // hard dependency. If Redis is unreachable we log and let the request through
        // rather than turning a cache outage into a full API outage. Catch DataAccessException
        // (Spring wraps RedisConnectionFailureException in it), NOT bare Exception, so a real
        // bug still surfaces instead of silently disabling the limiter.
        try {
            redis.opsForZSet().removeRangeByScore(key, 0, windowStart);
            Long requestCounts = redis.opsForZSet().zCard(key);
            long count = (requestCounts != null) ? requestCounts : 0;
            if (count >= MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("text/plain");
                response.getWriter().write("Too many requests. Please try again later.");
                return; // over limit: reject, do NOT continue the chain
            }
            String member = now + "-" + UUID.randomUUID();
            redis.opsForZSet().add(key, member, now);
            redis.expire(key, WINDOW);
        } catch (DataAccessException e) {
            log.warn("Rate limiter Redis unavailable, failing open for key {}: {}", key, e.getMessage());
        }

        // Reached by both the allowed path and the fail-open path (never after a 429).
        filterChain.doFilter(request, response);
    }

    /**
     * The bucket identity: the authenticated user id if present, else the client IP so
     * anonymous traffic (login/register brute force) is still bounded.
     */
    private String resolveClientId(HttpServletRequest request) {
        return RequestContext.currentUserId()
                .map(id -> "user:" + id)
                .orElseGet(() -> "ip:" + request.getRemoteAddr());
    }
}
