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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MicrometerMetricsCollector.
 * Tests that metric events are properly converted to Micrometer metrics.
 */
@DisplayName("Micrometer Metrics Collector Tests")
class MicrometerMetricsCollectorTest {
    
    private MeterRegistry registry;
    private MicrometerMetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new MicrometerMetricsCollector(registry);
    }
    
    @Test
    @DisplayName("Should record events appended metric")
    void shouldRecordEventsAppended() {
        // When
        collector.handleEventsAppended(new EventsAppendedMetric(5));
        
        // Then
        Counter counter = registry.find("eventstore.events.appended").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("Should record event type metric")
    void shouldRecordEventType() {
        // When
        collector.handleEventType(new EventTypeMetric("WalletOpened"));
        
        // Then
        Counter counter = registry.find("eventstore.events.by_type")
            .tag("event_type", "WalletOpened")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record concurrency violation metric")
    void shouldRecordConcurrencyViolation() {
        // When
        collector.handleConcurrencyViolation(new ConcurrencyViolationMetric());
        
        // Then
        Counter counter = registry.find("eventstore.concurrency.violations").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record command success metric")
    void shouldRecordCommandSuccess() {
        // When
        collector.handleCommandSuccess(new CommandSuccessMetric("deposit", Duration.ofMillis(150)));
        
        // Then
        Timer timer = registry.find("eventstore.commands.duration")
            .tag("command_type", "deposit")
            .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        
        Counter counter = registry.find("eventstore.commands.total")
            .tag("command_type", "deposit")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record command failure metric")
    void shouldRecordCommandFailure() {
        // When
        collector.handleCommandFailure(new CommandFailureMetric("withdraw", "validation"));
        
        // Then
        Counter counter = registry.find("eventstore.commands.failed")
            .tag("command_type", "withdraw")
            .tag("error_type", "validation")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record idempotent operation metric")
    void shouldRecordIdempotentOperation() {
        // When
        collector.handleIdempotentOperation(new IdempotentOperationMetric("open_wallet"));
        
        // Then
        Counter counter = registry.find("eventstore.commands.idempotent")
            .tag("command_type", "open_wallet")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record events published metric")
    void shouldRecordEventsPublished() {
        // When
        collector.handleEventsPublished(new EventsPublishedMetric("kafka-publisher", 3));
        
        // Then
        Counter counter = registry.find("outbox.events.published")
            .tag("publisher", "kafka-publisher")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }
    
    @Test
    @DisplayName("Should record processing cycle metric")
    void shouldRecordProcessingCycle() {
        // When
        collector.handleProcessingCycle(new ProcessingCycleMetric());
        
        // Then
        Counter counter = registry.find("outbox.processing.cycles").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record outbox error metric")
    void shouldRecordOutboxError() {
        // When
        collector.handleOutboxError(new OutboxErrorMetric("kafka-publisher"));
        
        // Then
        Counter counter = registry.find("outbox.errors")
            .tag("publisher", "kafka-publisher")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record leadership metric")
    void shouldRecordLeadership() {
        // When
        collector.handleLeadership(new LeadershipMetric("instance-1", true));
        
        // Then
        Gauge gauge = registry.find("outbox.is_leader")
            .tag("instance", "instance-1")
            .gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);
        
        // When: leadership changes
        collector.handleLeadership(new LeadershipMetric("instance-1", false));
        
        // Then: gauge should update
        assertThat(gauge.value()).isEqualTo(0.0);
    }
    
    @Test
    @DisplayName("Should handle command started metric (no-op, for logging)")
    void shouldHandleCommandStarted() {
        // When: command started event is published
        // (This is mainly for logging/debugging, no metrics recorded)
        collector.handleCommandStarted(new CommandStartedMetric("deposit", Instant.now()));
        
        // Then: no exception thrown
        // Command duration is recorded via CommandSuccessMetric
    }
}

