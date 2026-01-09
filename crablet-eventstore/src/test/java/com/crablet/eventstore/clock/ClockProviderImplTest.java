package com.crablet.eventstore.clock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for ClockProviderImpl.
 */
class ClockProviderImplTest {
    
    private final ClockProviderImpl clockProvider = new ClockProviderImpl();
    
    @AfterEach
    void tearDown() {
        clockProvider.resetToSystemClock();
    }
    
    @Test
    void testNowReturnsCurrentInstant() {
        Instant before = Instant.now();
        Instant result = clockProvider.now();
        Instant after = Instant.now();
        
        assertTrue(result.isAfter(before) || result.equals(before));
        assertTrue(result.isBefore(after) || result.equals(after));
    }
    
    @Test
    void testSetClockChangesTime() {
        Instant fixedTime = Instant.parse("2020-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedTime, ZoneOffset.UTC);
        
        clockProvider.setClock(fixedClock);
        
        assertEquals(fixedTime, clockProvider.now());
    }
    
    @Test
    void testMultipleCallsWithFixedClockReturnSameTime() {
        Instant fixedTime = Instant.parse("2021-06-15T12:30:45Z");
        Clock fixedClock = Clock.fixed(fixedTime, ZoneOffset.UTC);
        
        clockProvider.setClock(fixedClock);
        
        Instant time1 = clockProvider.now();
        // Sleep a bit to ensure time would have advanced
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }
        Instant time2 = clockProvider.now();
        
        assertEquals(fixedTime, time1);
        assertEquals(fixedTime, time2);
        assertEquals(time1, time2);
    }
    
    @Test
    void testGetClockReturnsCurrentClock() {
        Clock currentClock = clockProvider.getClock();
        
        assertNotNull(currentClock);
        assertEquals(Clock.systemUTC(), currentClock);
    }
    
    @Test
    void testGetClockAfterSetClock() {
        Clock customClock = Clock.systemDefaultZone();
        
        clockProvider.setClock(customClock);
        
        assertEquals(customClock, clockProvider.getClock());
    }
    
    @Test
    void testResetToSystemClock() {
        Clock fixedClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        
        clockProvider.setClock(fixedClock);
        clockProvider.resetToSystemClock();
        
        assertNotEquals(fixedClock, clockProvider.getClock());
        assertEquals(Clock.systemUTC(), clockProvider.getClock());
    }
    
    @Test
    void testClockAdvancesWithSystemDefaultZone() {
        Clock defaultZoneClock = Clock.systemDefaultZone();
        clockProvider.setClock(defaultZoneClock);
        
        Instant time1 = clockProvider.now();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }
        Instant time2 = clockProvider.now();
        
        assertTrue(time2.isAfter(time1) || time2.equals(time1));
    }
    
    @Test
    void testClockConsistency() {
        Instant time1 = clockProvider.now();
        Instant time2 = clockProvider.now();
        
        // With system clock, time should either be same or time2 >= time1
        assertTrue(time2.equals(time1) || time2.isAfter(time1));
        assertFalse(time2.isBefore(time1));
    }
}
