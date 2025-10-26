package com.crablet.eventstore.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics collection for EventStore operations.
 * Tracks command execution, events appended, and concurrency violations.
 */
@Component
public class EventStoreMetrics implements MeterBinder {
    
    private final MeterRegistry registry;
    private final Map<String, Counter> commandCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failedCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> commandTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> eventTypeCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> idempotentCounters = new ConcurrentHashMap<>();
    private Counter eventsAppendedCounter;
    private Counter concurrencyViolationsCounter;
    
    public EventStoreMetrics(MeterRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        // Register counters
        eventsAppendedCounter = Counter.builder("eventstore.events.appended")
            .description("Total number of events appended to store")
            .register(registry);
        
        concurrencyViolationsCounter = Counter.builder("eventstore.concurrency.violations")
            .description("Total number of DCB concurrency violations")
            .register(registry);
    }
    
    /**
     * Start timing a command execution.
     */
    public Timer.Sample startCommand(String commandType) {
        return Timer.start(registry);
    }
    
    /**
     * Record successful command execution.
     */
    public void recordCommandSuccess(String commandType, Timer.Sample sample) {
        Timer timer = commandTimers.computeIfAbsent(commandType, type ->
            Timer.builder("eventstore.commands.duration")
                .description("Command execution time")
                .tag("command_type", type)
                .register(registry)
        );
        sample.stop(timer);
        
        Counter counter = commandCounters.computeIfAbsent(commandType, type ->
            Counter.builder("eventstore.commands.total")
                .description("Total commands processed")
                .tag("command_type", type)
                .register(registry)
        );
        counter.increment();
    }
    
    /**
     * Record failed command execution.
     */
    public void recordCommandFailure(String commandType, String errorType) {
        String key = commandType + "." + errorType;
        Counter counter = failedCounters.computeIfAbsent(key, k -> {
            String[] parts = key.split("\\.", 2);
            String cmdType = parts.length > 0 ? parts[0] : "unknown";
            String errType = parts.length > 1 ? parts[1] : "unknown";
            return Counter.builder("eventstore.commands.failed")
                .description("Failed commands")
                .tag("command_type", cmdType)
                .tag("error_type", errType)
                .register(registry);
        });
        counter.increment();
    }
    
    /**
     * Record events appended to store.
     */
    public void recordEventsAppended(int count) {
        if (count > 0) {
            eventsAppendedCounter.increment(count);
        }
    }
    
    /**
     * Record concurrency violation (DCB optimistic locking failure).
     */
    public void recordConcurrencyViolation() {
        concurrencyViolationsCounter.increment();
    }
    
    /**
     * Record event type for distribution tracking.
     * Business-agnostic metric for operational monitoring.
     */
    public void recordEventType(String eventType) {
        if (eventType == null || eventType.isEmpty()) {
            return;
        }
        
        Counter counter = eventTypeCounters.computeIfAbsent(eventType, type ->
            Counter.builder("eventstore.events.by_type")
                .description("Events appended by type")
                .tag("event_type", type)
                .register(registry)
        );
        counter.increment();
    }
    
    /**
     * Record idempotent operation (duplicate request handled gracefully).
     */
    public void recordIdempotentOperation(String commandType) {
        if (commandType == null || commandType.isEmpty()) {
            return;
        }
        
        Counter counter = idempotentCounters.computeIfAbsent(commandType, type ->
            Counter.builder("eventstore.commands.idempotent")
                .description("Idempotent operations (duplicate requests)")
                .tag("command_type", type)
                .register(registry)
        );
        counter.increment();
    }
}

