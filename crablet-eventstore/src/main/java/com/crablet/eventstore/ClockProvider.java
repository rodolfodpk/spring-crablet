package com.crablet.eventstore;

import com.crablet.eventstore.internal.ClockProviderImpl;
import java.time.Clock;
import java.time.Instant;

/**
 * ClockProvider interface for providing timestamps.
 * Allows for testable time control and different clock implementations.
 */
public interface ClockProvider {

    /**
     * Get the current instant.
     *
     * @return Current instant
     */
    Instant now();

    /**
     * Set a fixed clock for testing purposes.
     * This method is primarily intended for test implementations.
     *
     * @param clock The clock to use
     */
    void setClock(Clock clock);

    /**
     * Reset to system clock.
     * This method is primarily intended for test cleanup.
     */
    void resetToSystemClock();

    /** Returns a {@link ClockProvider} backed by the system UTC clock. */
    static ClockProvider systemDefault() {
        return new ClockProviderImpl();
    }
}
