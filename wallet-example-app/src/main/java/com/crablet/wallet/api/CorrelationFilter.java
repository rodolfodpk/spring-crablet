package com.crablet.wallet.api;

import com.crablet.eventstore.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that binds a correlation ID to the current request's {@link ScopedValue} scope.
 * <p>
 * If the caller provides an {@code X-Correlation-ID} header, that value is used;
 * otherwise a fresh UUID is generated. The ID is echoed back in the response header
 * so callers can trace their request through logs and the events table.
 * <p>
 * The {@link ScopedValue} scope exits automatically when the filter chain returns —
 * no explicit cleanup is needed.
 */
@Component
public class CorrelationFilter extends OncePerRequestFilter {

    static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(CORRELATION_HEADER);
        UUID correlationId;
        try {
            correlationId = (header != null && !header.isBlank())
                ? UUID.fromString(header)
                : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            // Header value is not a valid UUID — generate a fresh one
            correlationId = UUID.randomUUID();
        }

        response.setHeader(CORRELATION_HEADER, correlationId.toString());

        try {
            ScopedValue.where(CorrelationContext.CORRELATION_ID, correlationId)
                       .call(() -> {
                           filterChain.doFilter(request, response);
                           return null;
                       });
        } catch (Exception e) {
            if (e instanceof ServletException se) throw se;
            if (e instanceof IOException ioe) throw ioe;
            throw new ServletException("Unexpected error in correlation filter", e);
        }
    }
}
