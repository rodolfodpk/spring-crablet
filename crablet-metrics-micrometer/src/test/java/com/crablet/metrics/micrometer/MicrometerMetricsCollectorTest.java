package com.crablet.metrics.micrometer;

import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.command.metrics.CommandStartedMetric;
import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import com.crablet.eventpoller.metrics.BackoffStateMetric;
import com.crablet.eventpoller.metrics.LeadershipMetric;
import com.crablet.eventstore.metrics.ConcurrencyViolationMetric;
import com.crablet.eventstore.metrics.MetricEvent;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.ProcessingCycleMetric;
import com.crablet.outbox.metrics.PublishingDurationMetric;
import com.crablet.views.metrics.ViewProjectionErrorMetric;
import com.crablet.views.metrics.ViewProjectionMetric;
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
@SuppressWarnings("NullAway")
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
        collector.handleMetricEvent(new EventsAppendedMetric(5));
        
        // Then
        Counter counter = registry.find("eventstore.events.appended").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("Should record event type metric")
    void shouldRecordEventType() {
        // When
        collector.handleMetricEvent(new EventTypeMetric("WalletOpened"));
        
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
        collector.handleMetricEvent(new ConcurrencyViolationMetric());
        
        // Then
        Counter counter = registry.find("eventstore.concurrency.violations").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record command success metric")
    void shouldRecordCommandSuccess() {
        // When
        collector.handleMetricEvent(new CommandSuccessMetric("deposit", Duration.ofMillis(150), "commutative"));
        
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
        collector.handleMetricEvent(new CommandFailureMetric("withdraw", "validation"));
        
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
        collector.handleMetricEvent(new IdempotentOperationMetric("open_wallet"));
        
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
        collector.handleMetricEvent(new EventsPublishedMetric("kafka-publisher", 3));
        
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
        collector.handleMetricEvent(new ProcessingCycleMetric());
        
        // Then
        Counter counter = registry.find("outbox.processing.cycles").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Should record outbox error metric")
    void shouldRecordOutboxError() {
        // When
        collector.handleMetricEvent(new OutboxErrorMetric("kafka-publisher"));
        
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
        collector.handleMetricEvent(new LeadershipMetric("outbox", "instance-1", true));

        // Then
        Gauge gauge = registry.find("processor.is_leader")
            .tag("processor", "outbox")
            .tag("instance_id", "instance-1")
            .gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);

        // When: leadership changes
        collector.handleMetricEvent(new LeadershipMetric("outbox", "instance-1", false));

        // Then: gauge should update
        assertThat(gauge.value()).isEqualTo(0.0);
    }
    
    @Test
    @DisplayName("Should record in-flight gauge on command started and decrement on success")
    void shouldRecordInflightGauge_OnCommandStartedAndSuccess() {
        // When: command started
        collector.handleMetricEvent(new CommandStartedMetric("deposit", Instant.now()));

        // Then: in-flight gauge is 1
        Gauge gauge = registry.find("commands.inflight").tag("command_type", "deposit").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);

        // When: command succeeds
        collector.handleMetricEvent(new CommandSuccessMetric("deposit", Duration.ofMillis(50), "commutative"));

        // Then: in-flight gauge is back to 0
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should record poller processing cycle with empty poll branch")
    void shouldRecordPollerProcessingCycleWithEmptyPoll() {
        collector.handleMetricEvent(
                new com.crablet.eventpoller.metrics.ProcessingCycleMetric("views", "inst-1", 0, true));

        Counter empty = registry.find("poller.empty.polls")
                .tag("processor", "views")
                .tag("instance_id", "inst-1")
                .counter();
        assertThat(empty).isNotNull();
        assertThat(empty.count()).isEqualTo(1.0);

        Counter cycles = registry.find("poller.processing.cycles")
                .tag("processor", "views")
                .counter();
        assertThat(cycles).isNotNull();
        assertThat(cycles.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record publishing duration, backoff, views, automations, and ignore unknown events")
    void shouldRecordAuxiliaryMetricsAndIgnoreUnknown() {
        collector.handleMetricEvent(new PublishingDurationMetric("pub-a", Duration.ofMillis(7)));

        collector.handleMetricEvent(new BackoffStateMetric("proc", "node", true, 4));

        collector.handleMetricEvent(new ViewProjectionMetric("balance", 2, Duration.ofMillis(11)));

        collector.handleMetricEvent(new ViewProjectionErrorMetric("balance"));

        collector.handleMetricEvent(new AutomationExecutionMetric("auto-1", 1, Duration.ofMillis(9)));

        collector.handleMetricEvent(new AutomationExecutionErrorMetric("auto-1"));

        collector.handleMetricEvent(new UnknownMetricForCollectorTest());

        assertThat(registry.find("outbox.publishing.duration").timer().count()).isEqualTo(1);
        assertThat(registry.find("poller.backoff.active").gauge().value()).isEqualTo(1.0);
        assertThat(registry.find("views.projection.duration").timer().count()).isEqualTo(1);
        assertThat(registry.find("views.projection.errors").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("automations.execution.duration").timer().count()).isEqualTo(1);
        assertThat(registry.find("automations.execution.errors").counter().count()).isEqualTo(1.0);
    }

    private record UnknownMetricForCollectorTest() implements MetricEvent {}
}

