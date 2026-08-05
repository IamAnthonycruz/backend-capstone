package com.readshelf.config;

import com.readshelf.book.BookResponseDTO;
import com.readshelf.user.UserProfileResponseDTO;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.annotation.EnableCaching;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

/**
 * Redis-backed caching (Phase 10).
 *
 * `@EnableCaching` turns on the annotation-driven cache aspect so `@Cacheable` /
 * `@CacheEvict` on service methods actually do something. Spring Boot already
 * auto-configures a {@code RedisCacheManager} (because {@code spring.cache.type: redis}),
 * so all we contribute here is:
 *   1. the DEFAULT cache configuration (JSON value serialization + a baseline TTL), and
 *   2. per-cache-region TTL overrides via a builder customizer.
 *
 * Serialization note (Spring Boot 4 / Jackson 3): each region caches exactly ONE type
 * (books -> BookResponseDTO, userProfiles -> UserProfileResponseDTO), so each uses a
 * type-bound {@link JacksonJsonRedisSerializer} constructed with that concrete class. The
 * serializer reads plain JSON straight back into the record — no {@code @class} type hint
 * in the payload, and no default-typing/security machinery.
 *
 * Why not the generic {@link GenericJacksonJsonRedisSerializer} for the regions? Without
 * default typing it writes untyped JSON, so a read deserializes to a LinkedHashMap and the
 * @Cacheable return cast fails (ClassCastException). Enabling default typing would embed
 * {@code @class}, but our DTOs are records (final), the standard NON_FINAL typing skips
 * final types, and name-based class resolution is fragile under devtools' RestartClassLoader.
 * A type-bound serializer sidesteps all of that. The generic one survives only as the
 * fallback for any unnamed region (see {@link #defaultCacheConfiguration()}).
 *
 * Keys stay plain strings so they're greppable in redis-cli.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache region names — referenced by the @Cacheable/@CacheEvict annotations. */
    public static final String BOOKS = "books";
    public static final String USER_PROFILES = "userProfiles";

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * String keys + a shared baseline (TTL, no null caching). Value serialization is left to
     * the caller so each region can bind its own concrete type.
     * `disableCachingNullValues` keeps a not-found lookup from poisoning the cache with a null.
     */
    private RedisCacheConfiguration baseConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));
    }

    /** A region whose values are exactly {@code type}: plain-JSON serialization, no @class hint. */
    private RedisCacheConfiguration typedConfiguration(Class<?> type, Duration ttl) {
        return baseConfiguration(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(type)));
    }

    /**
     * The fallback Boot hands to any cache region we DON'T name below. It has to be generic
     * (it can't know the type ahead of time), so it enables default typing to embed {@code @class}
     * — guarded by a validator restricted to our packages so an attacker can't coax Jackson into
     * instantiating arbitrary classes from cached JSON.
     */
    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.readshelf.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        return baseConfiguration(DEFAULT_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(typeValidator)
                                .build()));
    }

    /**
     * Per-region config: each names its value type (so reads round-trip cleanly) and its TTL.
     * Books change occasionally; profiles change rarely, so profiles hold longer.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer() {
        return builder -> builder
                .withCacheConfiguration(BOOKS,
                        typedConfiguration(BookResponseDTO.class, Duration.ofMinutes(10)))
                .withCacheConfiguration(USER_PROFILES,
                        typedConfiguration(UserProfileResponseDTO.class, Duration.ofMinutes(30)));
    }
}