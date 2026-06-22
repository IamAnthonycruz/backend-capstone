package com.readshelf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ⚠️ TEMPORARY DEV SECURITY — Phase 2 only.
 *
 * Permits every request and disables CSRF so the Book CRUD endpoints can be
 * exercised (Postman/curl) without the default generated password or CSRF tokens.
 *
 * This is NOT real security. It MUST be replaced in Phase 5 with:
 *   - stateless JWT authentication
 *   - role-based authorization (@PreAuthorize)
 *   - CSRF left disabled only because the API is token-based & stateless
 *
 * TODO(Phase 5): replace anyRequest().permitAll() with real auth rules.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}