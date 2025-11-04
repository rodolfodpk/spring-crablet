package com.crablet.metrics.micrometer;

import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.CommandStartedMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import com.crablet.eventstore.metrics.ConcurrencyViolationMetric;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.LeadershipMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.ProcessingCycleMetric;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics collector for Crablet metric events.
 * <p>
 * Automatically subscribes to metric events published via Spring Events and records them to Micrometer.
 * <p>
 * This collector is auto-discovered by Spring when {@code crablet-metrics-micrometer} is on the classpath.
 */
@Component
public class MicrometerMetricsCollector {
    
    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricsCollector.class);
    
    private final MeterRegistry registry;
    
    // Track leadership state per instance (instanceId -> isLeader)
    private final Map<String, AtomicInteger> leadershipState = new ConcurrentHashMap<>();
    
    public MicrometerMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        log.info("MicrometerMetricsCollector initialized");
    }
    
    @EventListener
    public void handleEventsAppended(EventsAppendedMetric event) {
        Counter.builder("eventstore.events.appended")
            .description("Total number of events appended to store")
            .register(registry)
            .increment(event.count());
    }
    
    @EventListener
    public void handleEventType(EventTypeMetric event) {
        Counter.builder("eventstore.events.by_type")
            .description("Events appended by type")
            .tag("event_type", event.eventType())
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleConcurrencyViolation(ConcurrencyViolationMetric event) {
        Counter.builder("eventstore.concurrency.violations")
            .description("Total number of DCB concurrency violations")
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleCommandStarted(CommandStartedMetric event) {
        // Start time is recorded, but we wait for CommandSuccessMetric for duration
        // This event is mainly for logging/debugging if needed
        log.debug("Command started: {} at {}", event.commandType(), event.startTime());
    }
    
    @EventListener
    public void handleCommandSuccess(CommandSuccessMetric event) {
        // Record duration using the Duration from the event (calculated via ClockProvider)
        Timer.builder("eventstore.commands.duration")
            .description("Command execution time")
            .tag("command_type", event.commandType())
            .register(registry)
            .record(event.duration());
        
        Counter.builder("eventstore.commands.total")
            .description("Total commands processed")
            .tag("command_type", event.commandType())
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleCommandFailure(CommandFailureMetric event) {
        Counter.builder("eventstore.commands.failed")
            .description("Failed commands")
            .tag("command_type", event.commandType())
            .tag("error_type", event.errorType())
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleIdempotentOperation(IdempotentOperationMetric event) {
        Counter.builder("eventstore.commands.idempotent")
            .description("Idempotent operations (duplicate requests)")
            .tag("command_type", event.commandType())
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleEventsPublished(EventsPublishedMetric event) {
        Counter.builder("outbox.events.published")
            .description("Total number of events published")
            .tag("publisher", event.publisherName())
            .register(registry)
            .increment(event.count());
    }
    
    @EventListener
    public void handleProcessingCycle(ProcessingCycleMetric event) {
        Counter.builder("outbox.processing.cycles")
            .description("Total number of processing cycles")
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleOutboxError(OutboxErrorMetric event) {
        Counter.builder("outbox.errors")
            .description("Total number of publishing errors")
            .tag("publisher", event.publisherName())
            .register(registry)
            .increment();
    }
    
    @EventListener
    public void handleLeadership(LeadershipMetric event) {
        // Update leadership state
        AtomicInteger leaderValue = leadershipState.computeIfAbsent(
            event.instanceId(), 
            k -> {
                AtomicInteger value = new AtomicInteger(0);
                // Register gauge for this instance
                Gauge.builder("outbox.is_leader", value, AtomicInteger::get)
                    .description("Whether this instance is the outbox leader (1=leader, 0=follower)")
                    .tag("instance", k)
                    .register(registry);
                return value;
            }
        );
        
        leaderValue.set(event.isLeader() ? 1 : 0);
    }
}

