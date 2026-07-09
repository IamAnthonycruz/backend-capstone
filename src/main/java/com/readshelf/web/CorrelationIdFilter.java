package com.readshelf.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stamps every request with a correlation id so a single request can be traced across
 * every log line it produces. Runs in the OUTER servlet chain (registered as a plain
 * @Component filter), BEFORE Spring Security — so even a request rejected with a 401
 * still carries an id.
 *
 * HIGHEST_PRECEDENCE: this must be the first thing to touch the request, because every
 * later filter (logging, rate limit) wants the id to already be present.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Header we read (inbound) and write (outbound). */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** MDC key the logging pattern will reference. */
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{1,50}$");
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
       String correlationId =  request.getHeader(CORRELATION_ID_HEADER);
        if (!isValidId(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            // 2. Put the id into MDC and onto the response header
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // 3. Continue the chain
            filterChain.doFilter(request, response);
        } finally {
            // 4. CRITICAL: clear MDC in a finally block to prevent thread leakage
            MDC.remove(MDC_KEY);
        }
    }
    /**
     * Checks if the provided ID is non-null, bounded in length, and free of
     * unsafe characters to mitigate log injection or payload attacks.
     */
    private boolean isValidId(String id) {
        return id != null && VALID_ID_PATTERN.matcher(id).matches();
    }
}
