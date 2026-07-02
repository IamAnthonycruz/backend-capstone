package com.readshelf.config;

import com.readshelf.auth.JwtAuthFilter;
import com.readshelf.auth.JwtService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security (Phase 5).
 *
 *  - No session: every request re-authenticates from its Bearer token (SecurityContext
 *    is not persisted between requests).
 *  - CSRF disabled: CSRF defends session-cookie auth; a token sent in the Authorization
 *    header is immune (the browser won't auto-attach it cross-site). Safe to leave off.
 *  - JwtAuthFilter runs before the username/password filter, translating "valid token"
 *    into an authenticated SecurityContext for the authorization layer to read.
 *  - @EnableMethodSecurity turns on @PreAuthorize for the per-method rules (next step).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ERROR dispatch: when a handler/filter calls sendError(...), the
                        // container re-dispatches to /error. OncePerRequestFilter skips that
                        // pass, so the context is empty there — without this, every secured
                        // sendError (e.g. a 403 from @PreAuthorize) collapses into a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Public: auth endpoints (chicken-and-egg) + health probes.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health/**").permitAll()
                        // Everything else requires a valid token.
                        .anyRequest().authenticated()
                )
                // Unauthenticated hit on a protected route -> clean 401 (no login-form redirect).
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        (request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password hashing for register (encode) and login (matches).
     * BCrypt: salted, adaptive work factor — never store or compare plaintext.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
