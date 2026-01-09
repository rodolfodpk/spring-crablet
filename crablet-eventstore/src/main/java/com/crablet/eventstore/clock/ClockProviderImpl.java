package com.crablet.eventstore.clock;

import java.time.Clock;
import java.time.Instant;

/**
 * Clock service implementation for providing consistent timestamps across the application.
 * Allows tests to control time using Clock.fixed() for deterministic behavior.
 * <p>
 * <strong>Spring Integration:</strong>
 * Users must define as @Bean:
 * <pre>{@code
 * @Bean
 * @Primary
 * public ClockProvider clockProvider() {
 *     return new ClockProviderImpl();
 * }
 * }</pre>
 */
public class ClockProviderImpl implements ClockProvider {
    private Clock clock = Clock.systemUTC();
    
    /**
     * Get the current instant using the configured clock.
     * 
     * @return Current instant
     */
    @Override
    public Instant now() {
        return Instant.now(clock);
    }
    
    /**
     * Set the clock to use for time operations.
     * Primarily used in tests for deterministic behavior.
     * 
     * @param clock The clock to use
     */
    @Override
    public void setClock(Clock clock) {
        this.clock = clock;
    }
    
    /**
     * Get the current clock instance.
     * 
     * @return The current clock
     */
    public Clock getClock() {
        return clock;
    }
    
    /**
     * Reset to system clock (useful for test cleanup).
     */
    @Override
    public void resetToSystemClock() {
        this.clock = Clock.systemUTC();
    }
}
