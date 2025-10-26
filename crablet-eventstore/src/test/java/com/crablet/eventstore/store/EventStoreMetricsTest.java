package com.crablet.eventstore.store;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for EventStoreMetrics.
 */
class EventStoreMetricsTest {
    
    private EventStoreMetrics metrics;
    private MeterRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new EventStoreMetrics(registry);
        metrics.bindTo(registry);
    }
    
    @Test
    void testRecordEventsAppended() {
        metrics.recordEventsAppended(5);
        
        Counter counter = registry.find("eventstore.events.appended").counter();
        assertNotNull(counter);
        assertEquals(5, counter.count());
    }
    
    @Test
    void testRecordEventsAppendedZero() {
        metrics.recordEventsAppended(0);
        
        Counter counter = registry.find("eventstore.events.appended").counter();
        assertNotNull(counter);
        assertEquals(0, counter.count());
    }
    
    @Test
    void testRecordEventsAppendedNegative() {
        metrics.recordEventsAppended(-1);
        
        Counter counter = registry.find("eventstore.events.appended").counter();
        assertNotNull(counter);
        assertEquals(0, counter.count(), "Negative count should not increment");
    }
    
    @Test
    void testRecordConcurrencyViolation() {
        metrics.recordConcurrencyViolation();
        metrics.recordConcurrencyViolation();
        
        Counter counter = registry.find("eventstore.concurrency.violations").counter();
        assertNotNull(counter);
        assertEquals(2, counter.count());
    }
    
    @Test
    void testRecordEventType() {
        metrics.recordEventType("wallet_opened");
        metrics.recordEventType("deposit_made");
        
        // Each event type creates its own counter
        long totalCount = registry.find("eventstore.events.by_type").counters().stream()
            .mapToLong(c -> (long) c.count())
            .sum();
        assertEquals(2, totalCount);
    }
    
    @Test
    void testRecordEventTypeNull() {
        metrics.recordEventType(null);
        
        Counter counter = registry.find("eventstore.events.by_type").counter();
        assertNull(counter, "Null event type should not create counter");
    }
    
    @Test
    void testRecordEventTypeEmpty() {
        metrics.recordEventType("");
        
        Counter counter = registry.find("eventstore.events.by_type").counter();
        assertNull(counter, "Empty event type should not create counter");
    }
    
    @Test
    void testMultipleEventTypes() {
        metrics.recordEventType("type1");
        metrics.recordEventType("type2");
        metrics.recordEventType("type1");
        
        // Verify multiple counters created
        double totalCount = registry.find("eventstore.events.by_type").counters().stream()
            .mapToDouble(Counter::count)
            .sum();
        assertEquals(3.0, totalCount);
    }
    
    @Test
    void testRecordIdempotentOperation() {
        metrics.recordIdempotentOperation("open_wallet");
        metrics.recordIdempotentOperation("deposit");
        
        // Each command type creates its own counter
        long totalCount = registry.find("eventstore.commands.idempotent").counters().stream()
            .mapToLong(c -> (long) c.count())
            .sum();
        assertEquals(2, totalCount);
    }
    
    @Test
    void testRecordIdempotentOperationNull() {
        metrics.recordIdempotentOperation(null);
        
        Counter counter = registry.find("eventstore.commands.idempotent").counter();
        assertNull(counter, "Null command type should not create counter");
    }
    
    @Test
    void testRecordIdempotentOperationEmpty() {
        metrics.recordIdempotentOperation("");
        
        Counter counter = registry.find("eventstore.commands.idempotent").counter();
        assertNull(counter, "Empty command type should not create counter");
    }
    
    @Test
    void testStartCommand() {
        Timer.Sample sample = metrics.startCommand("test_command");
        
        assertNotNull(sample);
    }
    
    @Test
    void testRecordCommandSuccess() {
        Timer.Sample sample = metrics.startCommand("test_command");
        
        metrics.recordCommandSuccess("test_command", sample);
        
        // Verify timer was stopped
        Counter counter = registry.find("eventstore.commands.total").counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }
    
    @Test
    void testRecordCommandFailure() {
        metrics.recordCommandFailure("open_wallet", "wallet_exists");
        
        Counter counter = registry.find("eventstore.commands.failed").counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }
    
    @Test
    void testMultipleCommandFailures() {
        metrics.recordCommandFailure("open_wallet", "wallet_exists");
        metrics.recordCommandFailure("deposit", "insufficient_funds");
        metrics.recordCommandFailure("open_wallet", "invalid_input");
        
        // Each unique command.error pair creates its own counter
        long totalCount = registry.find("eventstore.commands.failed").counters().stream()
            .mapToLong(c -> (long) c.count())
            .sum();
        assertEquals(3, totalCount);
    }
    
    @Test
    void testMultipleSameCommandType() {
        String commandType = "test_command";
        
        Timer.Sample sample1 = metrics.startCommand(commandType);
        metrics.recordCommandSuccess(commandType, sample1);
        
        Timer.Sample sample2 = metrics.startCommand(commandType);
        metrics.recordCommandSuccess(commandType, sample2);
        
        Counter counter = registry.find("eventstore.commands.total").counter();
        assertEquals(2, counter.count());
    }
    
    @Test
    void testEventTypeCounterReusesExisting() {
        metrics.recordEventType("wallet_opened");
        metrics.recordEventType("wallet_opened");
        
        double totalCount = registry.find("eventstore.events.by_type").counters().stream()
            .mapToDouble(Counter::count)
            .sum();
        assertEquals(2.0, totalCount);
    }
    
    @Test
    void testIdempotentCounterReusesExisting() {
        String commandType = "test_command";
        
        metrics.recordIdempotentOperation(commandType);
        metrics.recordIdempotentOperation(commandType);
        
        Counter counter = registry.find("eventstore.commands.idempotent").counter();
        assertEquals(2, counter.count());
    }
    
    @Test
    void testBindToRegistersCounters() {
        SimpleMeterRegistry newRegistry = new SimpleMeterRegistry();
        EventStoreMetrics newMetrics = new EventStoreMetrics(newRegistry);
        
        newMetrics.bindTo(newRegistry);
        
        assertNotNull(newRegistry.find("eventstore.events.appended").counter());
        assertNotNull(newRegistry.find("eventstore.concurrency.violations").counter());
    }
}
