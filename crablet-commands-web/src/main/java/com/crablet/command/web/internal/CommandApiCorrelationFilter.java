package com.crablet.command.web.internal;

import com.crablet.command.web.CommandApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Optional correlation header handling scoped to the generic command API endpoint.
 */
class CommandApiCorrelationFilter extends OncePerRequestFilter {

    static final String CORRELATION_ID_ATTRIBUTE = CommandApiCorrelationFilter.class.getName() + ".correlationId";

    private final CommandApiProperties properties;

    CommandApiCorrelationFilter(CommandApiProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isCorrelationHeaderEnabled() || !isCommandApiRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID correlationId;
        String headerName = properties.getCorrelationHeaderName();
        String rawHeader = request.getHeader(headerName);
        if (rawHeader == null || rawHeader.isBlank()) {
            correlationId = UUID.randomUUID();
        } else {
            try {
                correlationId = UUID.fromString(rawHeader.trim());
            } catch (IllegalArgumentException e) {
                writeInvalidCorrelationHeader(response, headerName);
                return;
            }
        }

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(headerName, correlationId.toString());
        filterChain.doFilter(request, response);
    }

    private boolean isCommandApiRequest(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return normalizePath(properties.getBasePath()).equals(normalizePath(requestPath));
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static void writeInvalidCorrelationHeader(HttpServletResponse response, String headerName) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"%s","title":"Bad Request","status":400,"detail":"Invalid %s header: expected UUID"}
                """.formatted(CommandApiProblemTypes.BAD_REQUEST, headerName));
    }
}
