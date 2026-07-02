package com.readshelf.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request from its Bearer token (if present). It ONLY authenticates —
 * it never rejects. "Is this endpoint allowed?" is the authorization layer's job
 * (SecurityConfig). Not a @Component on purpose: it's wired explicitly into the
 * security filter chain in SecurityConfig, which avoids double-registration.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No bearer token -> nothing to authenticate. Continue; the authorization
        // rules will decide whether this endpoint actually required one.
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7); // strip "Bearer "
        try {
            Claims claim = jwtService.parseClaims(token);
            String userId = claim.getSubject();
            String role = claim.get("role", String.class);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var auth = new  UsernamePasswordAuthenticationToken(userId,null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch(JwtException ignored) {
        }


        // Whether or not we authenticated above, the request MUST continue down the chain.
        filterChain.doFilter(request, response);
    }
}
