package com.wallets.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for database operations and rate limiting.
 * Provides circuit breaker, retry, timeout, and rate limiting protection.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Circuit breaker for database operations.
     * Opens after 50% failure rate in 10 calls, stays open for 30 seconds.
     */
    @Bean
    public CircuitBreaker databaseCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();

        return CircuitBreaker.of("database", config);
    }

    /**
     * Retry configuration for database operations.
     * Retries up to 3 times with 1 second delay for database exceptions.
     */
    @Bean
    public Retry databaseRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .retryExceptions(
                        org.springframework.dao.DataAccessException.class,
                        java.sql.SQLException.class
                )
                .build();

        return Retry.of("database", config);
    }

    /**
     * Time limiter for database operations.
     * Times out after 10 seconds and cancels running futures.
     */
    @Bean
    public TimeLimiter databaseTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();

        return TimeLimiter.of("database", config);
    }

    /**
     * Global API rate limiter - prevents total system overload.
     * Limits total requests across all endpoints to prevent resource exhaustion.
     */
    @Bean
    public RateLimiter globalApiRateLimiter(
            @Value("${resilience4j.ratelimiter.instances.globalApi.limit-for-period:1000}") int limitForPeriod) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)  // Configurable limit
                .limitRefreshPeriod(Duration.ofSeconds(1))  // per second
                .timeoutDuration(Duration.ZERO)  // Fail fast, no waiting
                .build();

        return RateLimiter.of("globalApi", config);
    }

}
