package com.readshelf.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Writes one "access log" line per request: method, path, status, duration.
 *
 * Runs just AFTER CorrelationIdFilter (HIGHEST_PRECEDENCE + 1) so the correlation id is
 * already in MDC and rides along on this line automatically. The line is written AFTER
 * the chain completes — status and duration only exist once the request has finished —
 * and in a finally, so even a request that blows up still gets logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            var duration = System.currentTimeMillis() - start;
            var method = request.getMethod();
            var path  = request.getRequestURI();
            var status = response.getStatus();
            log.info("{} {} -> {} ({}ms)", method, path, status, duration);

        }
    }
}
