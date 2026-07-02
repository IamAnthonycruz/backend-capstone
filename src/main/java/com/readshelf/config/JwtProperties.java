package com.readshelf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed binding for the {@code readshelf.security.jwt.*} keys in application.yml.
 * Durations bind from ISO-8601 strings (PT15M = 15 min, P7D = 7 days).
 */
@ConfigurationProperties(prefix = "readshelf.security.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}