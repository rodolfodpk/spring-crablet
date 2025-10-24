package com.wallets.infrastructure.web;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler for rate limiting.
 * Converts RequestNotPermitted exceptions to proper HTTP 429 responses.
 */
@RestControllerAdvice
public class RateLimitExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitExceptionHandler.class);

    /**
     * Handle rate limit exceeded exceptions.
     *
     * @param ex The RequestNotPermitted exception
     * @return ResponseEntity with 429 status and error details
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(RequestNotPermitted ex) {
        logger.warn("Rate limit exceeded: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", Map.of(
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message", "Too many requests. Please try again later.",
                        "timestamp", Instant.now().toString()
                )
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")  // Retry after 60 seconds
                .header("X-RateLimit-Limit", "50")  // Per-wallet limit
                .header("X-RateLimit-Window", "60")  // 60 seconds window
                .body(body);
    }
}

