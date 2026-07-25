package com.readshelf.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-open caching (Phase 10).
 *
 * By DEFAULT Spring uses {@code SimpleCacheErrorHandler}, which RETHROWS anything the cache
 * store throws. That makes Redis a hard dependency: with Redis down, the exception escapes the
 * @Cacheable interceptor before the method body runs, so the DB is never consulted and the
 * caller gets a 500. A cache outage should degrade, not take the API down.
 *
 * Same philosophy as RateLimitFilter's Redis fail-open in Phase 8.
 *
 * Implementing {@link CachingConfigurer} lets us override just the error handler while leaving
 * Boot's auto-configured RedisCacheManager (and CacheConfig's TTLs) untouched.
 */
@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheErrorConfig.class);

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            // READ failure: safe to swallow. Returning normally makes Spring treat it as a
            // cache MISS, so it proceeds to the method body and reads the DB. Client sees a
            // normal 200, just slower.
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed (serving from DB) — cache={} key={}: {}",
                        cache.getName(), key, exception.getMessage());
            }

            // WRITE-TO-CACHE failure: safe to swallow. The DB write already succeeded; we just
            // didn't get to warm the cache. Next read repopulates.
            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed (entry not cached) — cache={} key={}: {}",
                        cache.getName(), key, exception.getMessage());
            }

            // EVICT failure: swallowed DELIBERATELY — this is the one with a real tradeoff.
            // A Redis outage must not block writes: failing PUT /books/{id} because the *cache*
            // is unreachable trades a real outage for an optimization. The cost is that the old
            // entry survives, so once Redis recovers readers can see stale data — but that
            // window is bounded by the region TTL in CacheConfig, which is exactly why the TTL
            // exists alongside the evictions rather than instead of them.
            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICTION failed (entry not evicted) - cache={} key={}: {}",
                        cache.getName(), key, exception.getMessage());
            }

            // CLEAR failure: same call as evict, at whole-region scale — swallowed for the
            // same reason (a cache outage must not fail the caller).
            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed — cache={}: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}