package com.crablet.core.impl;

import com.crablet.core.ClockProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Clock service implementation for providing consistent timestamps across the application.
 * Allows tests to control time using Clock.fixed() for deterministic behavior.
 */
@Component
public class ClockProviderImpl implements ClockProvider {
    private java.time.Clock clock = java.time.Clock.systemUTC();
    
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
    public void setClock(java.time.Clock clock) {
        this.clock = clock;
    }
    
    /**
     * Get the current clock instance.
     * 
     * @return The current clock
     */
    public java.time.Clock getClock() {
        return clock;
    }
    
    /**
     * Reset to system clock (useful for test cleanup).
     */
    @Override
    public void resetToSystemClock() {
        this.clock = java.time.Clock.systemUTC();
    }
}
