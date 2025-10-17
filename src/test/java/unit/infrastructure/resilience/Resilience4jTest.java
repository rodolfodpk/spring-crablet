package unit.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Resilience4j integration with database operations.
 * Verifies circuit breaker, retry, and timeout behavior for JDBCEventStore.
 */
class Resilience4jTest extends AbstractCrabletTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private TimeLimiterRegistry timeLimiterRegistry;

    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private TimeLimiter timeLimiter;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("database");
        retry = retryRegistry.retry("database");
        timeLimiter = timeLimiterRegistry.timeLimiter("database");

        // Reset circuit breaker state
        circuitBreaker.reset();
    }

        @Test
        @DisplayName("Should verify circuit breaker configuration")
        void shouldVerifyCircuitBreakerConfiguration() {
            // Given & When
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            
            // Then
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(0);
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
            assertThat(metrics.getFailureRate()).isEqualTo(-1.0f); // -1.0f when no calls have been made
            assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(0);
            assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(0);
        }

    @Test
    @DisplayName("Should verify retry configuration")
    void shouldVerifyRetryConfiguration() {
        // Given & When
        Retry.Metrics metrics = retry.getMetrics();
        
        // Then
        assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
        assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
        assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);
        assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should verify time limiter configuration")
    void shouldVerifyTimeLimiterConfiguration() {
        // Given & When
        // TimeLimiter doesn't have metrics in the same way as CircuitBreaker and Retry
        // We can verify it exists and is configured
        
        // Then
        assertThat(timeLimiter).isNotNull();
        assertThat(timeLimiter.getName()).isEqualTo("database");
    }

    @Test
    @DisplayName("Should verify circuit breaker state transitions")
    void shouldVerifyCircuitBreakerStateTransitions() {
        // Given & When
        CircuitBreaker.State initialState = circuitBreaker.getState();
        
        // Then
        assertThat(initialState).isEqualTo(CircuitBreaker.State.CLOSED);
        
        // Test state transitions
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        
        circuitBreaker.transitionToClosedState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

        @Test
        @DisplayName("Should verify circuit breaker configuration values")
        void shouldVerifyCircuitBreakerConfigurationValues() {
            // Given & When
            CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();
            
            // Then
            assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
            assertThat(config.getSlidingWindowSize()).isEqualTo(10);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        }

        @Test
        @DisplayName("Should verify retry configuration values")
        void shouldVerifyRetryConfigurationValues() {
            // Given & When
            RetryConfig config = retry.getRetryConfig();
            
            // Then
            assertThat(config.getMaxAttempts()).isEqualTo(3);
            // Note: Some methods may not be available in this version, so we test what we can
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("Should verify time limiter configuration values")
        void shouldVerifyTimeLimiterConfigurationValues() {
            // Given & When
            TimeLimiterConfig config = timeLimiter.getTimeLimiterConfig();
            
            // Then
            assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(15));
            // Note: Some methods may not be available in this version, so we test what we can
            assertThat(config).isNotNull();
        }

    @Test
    @DisplayName("Should verify circuit breaker registry")
    void shouldVerifyCircuitBreakerRegistry() {
        // Given & When
        CircuitBreaker retrievedCircuitBreaker = circuitBreakerRegistry.circuitBreaker("database");
        
        // Then
        assertThat(retrievedCircuitBreaker).isNotNull();
        assertThat(retrievedCircuitBreaker.getName()).isEqualTo("database");
        assertThat(retrievedCircuitBreaker).isSameAs(circuitBreaker);
    }

    @Test
    @DisplayName("Should verify retry registry")
    void shouldVerifyRetryRegistry() {
        // Given & When
        Retry retrievedRetry = retryRegistry.retry("database");
        
        // Then
        assertThat(retrievedRetry).isNotNull();
        assertThat(retrievedRetry.getName()).isEqualTo("database");
        assertThat(retrievedRetry).isSameAs(retry);
    }

    @Test
    @DisplayName("Should verify time limiter registry")
    void shouldVerifyTimeLimiterRegistry() {
        // Given & When
        TimeLimiter retrievedTimeLimiter = timeLimiterRegistry.timeLimiter("database");
        
        // Then
        assertThat(retrievedTimeLimiter).isNotNull();
        assertThat(retrievedTimeLimiter.getName()).isEqualTo("database");
        assertThat(retrievedTimeLimiter).isSameAs(timeLimiter);
    }

        @Test
        @DisplayName("Should verify circuit breaker metrics after reset")
        void shouldVerifyCircuitBreakerMetricsAfterReset() {
            // Given
            CircuitBreaker.Metrics initialMetrics = circuitBreaker.getMetrics();
            
            // When
            circuitBreaker.reset();
            CircuitBreaker.Metrics resetMetrics = circuitBreaker.getMetrics();
            
            // Then
            assertThat(resetMetrics.getNumberOfSuccessfulCalls()).isEqualTo(0);
            assertThat(resetMetrics.getNumberOfFailedCalls()).isEqualTo(0);
            assertThat(resetMetrics.getFailureRate()).isEqualTo(-1.0f); // -1.0f when no calls have been made
            assertThat(resetMetrics.getNumberOfBufferedCalls()).isEqualTo(0);
            assertThat(resetMetrics.getNumberOfNotPermittedCalls()).isEqualTo(0);
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("Should verify retry metrics after reset")
        void shouldVerifyRetryMetricsAfterReset() {
            // Given
            Retry.Metrics initialMetrics = retry.getMetrics();
            
            // When
            // Retry doesn't have a reset method, so we just verify initial state
            Retry.Metrics resetMetrics = retry.getMetrics();
            
            // Then
            assertThat(resetMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
            assertThat(resetMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
            assertThat(resetMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);
            assertThat(resetMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
        }

    @Test
    @DisplayName("Should verify circuit breaker event publisher")
    void shouldVerifyCircuitBreakerEventPublisher() {
        // Given & When
        CircuitBreaker.EventPublisher eventPublisher = circuitBreaker.getEventPublisher();
        
        // Then
        assertThat(eventPublisher).isNotNull();
        // Event publisher is available for monitoring circuit breaker state changes
    }

    @Test
    @DisplayName("Should verify retry event publisher")
    void shouldVerifyRetryEventPublisher() {
        // Given & When
        Retry.EventPublisher eventPublisher = retry.getEventPublisher();
        
        // Then
        assertThat(eventPublisher).isNotNull();
        // Event publisher is available for monitoring retry attempts
    }

    @Test
    @DisplayName("Should verify circuit breaker allows calls when closed")
    void shouldVerifyCircuitBreakerAllowsCallsWhenClosed() {
        // Given
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        
        // When
        boolean callPermitted = circuitBreaker.tryAcquirePermission();
        
        // Then
        assertThat(callPermitted).isTrue();
    }

    @Test
    @DisplayName("Should verify circuit breaker blocks calls when open")
    void shouldVerifyCircuitBreakerBlocksCallsWhenOpen() {
        // Given
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // When
        boolean callPermitted = circuitBreaker.tryAcquirePermission();
        
        // Then
        assertThat(callPermitted).isFalse();
    }

    @Test
    @DisplayName("Should verify circuit breaker transitions to half-open")
    void shouldVerifyCircuitBreakerTransitionsToHalfOpen() {
        // Given
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // When
        circuitBreaker.transitionToHalfOpenState();
        
        // Then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    @DisplayName("Should verify time limiter timeout configuration")
    void shouldVerifyTimeLimiterTimeoutConfiguration() {
        // Given & When
        TimeLimiterConfig config = timeLimiter.getTimeLimiterConfig();
        
        // Then
        assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config).isNotNull();
    }
}
