package com.readshelf.web;

import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only facade over the per-request thread-local state, so business code can ask
 * "who is this / what's the trace id" without touching Spring Security's or SLF4J's APIs
 * directly. NOT a filter — it only READS what the filters (JwtAuthFilter, CorrelationIdFilter)
 * already put on the thread.
 *
 * Utility class: private constructor, all-static. Only valid on a request-handling thread —
 * an @Async/background thread does NOT inherit this state (a real gotcha for later phases).
 */
public final class RequestContext {

    private RequestContext() {
    } // no instances

    /**
     * The correlation id stamped by CorrelationIdFilter, or null if we're off-request.
     */
    public static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    /**
     * The authenticated user's id, or empty when the request is anonymous.
     */
    public static Optional<String> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String userId = auth.getPrincipal().toString();

        return Optional.of(userId);
    }


    public static Set<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_")? role.substring(5):role)
                .collect(Collectors.toUnmodifiableSet());

    }
}
