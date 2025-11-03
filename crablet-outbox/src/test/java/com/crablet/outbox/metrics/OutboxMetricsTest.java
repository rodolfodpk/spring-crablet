package com.crablet.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OutboxMetrics.
 * Tests metrics registration and recording.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxMetrics Unit Tests")
class OutboxMetricsTest {

    @Mock
    private Environment environment;

    private MeterRegistry meterRegistry;
    private OutboxMetrics outboxMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        when(environment.getProperty("HOSTNAME")).thenReturn(null);
        when(environment.getProperty("crablet.instance.id")).thenReturn(null);
        
        outboxMetrics = new OutboxMetrics(environment);
        outboxMetrics.bindTo(meterRegistry);
    }

    @Test
    @DisplayName("Should get instanceId from HOSTNAME environment variable")
    void shouldGetInstanceId_FromHOSTNAMEEnvironmentVariable() {
        // Given
        when(environment.getProperty("HOSTNAME")).thenReturn("pod-123");

        // When
        OutboxMetrics metrics = new OutboxMetrics(environment);

        // Then
        assertThat(metrics.getInstanceId()).isEqualTo("pod-123");
    }

    @Test
    @DisplayName("Should get instanceId from custom instance id config")
    void shouldGetInstanceId_FromCustomInstanceIdConfig() {
        // Given
        when(environment.getProperty("HOSTNAME")).thenReturn(null);
        when(environment.getProperty("crablet.instance.id")).thenReturn("custom-instance-1");

        // When
        OutboxMetrics metrics = new OutboxMetrics(environment);

        // Then
        assertThat(metrics.getInstanceId()).isEqualTo("custom-instance-1");
    }

    @Test
    @DisplayName("Should fall back to hostname when no environment variables")
    void shouldFallBackToHostname_WhenNoEnvironmentVariables() {
        // Given
        when(environment.getProperty("HOSTNAME")).thenReturn(null);
        when(environment.getProperty("crablet.instance.id")).thenReturn(null);

        // When
        OutboxMetrics metrics = new OutboxMetrics(environment);

        // Then - Should not be null (hostname fallback or unknown timestamp)
        assertThat(metrics.getInstanceId()).isNotNull();
        assertThat(metrics.getInstanceId()).isNotEmpty();
    }

    @Test
    @DisplayName("Should register all counters in bindTo")
    void shouldRegisterAllCounters_InBindTo() {
        // When - Already done in setUp

        // Then - Verify counters exist
        assertThat(meterRegistry.find("outbox.events.published").counter()).isNotNull();
        assertThat(meterRegistry.find("outbox.processing.cycles").counter()).isNotNull();
        assertThat(meterRegistry.find("outbox.errors").counter()).isNotNull();
        assertThat(meterRegistry.find("outbox.auto_pause_total").counter()).isNotNull();
    }

    @Test
    @DisplayName("Should register isLeader gauge with instance tag")
    void shouldRegisterIsLeaderGauge_WithInstanceTag() {
        // When - Already done in setUp

        // Then - Verify gauge exists
        Gauge gauge = meterRegistry.find("outbox.is_leader").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getTag("instance")).isEqualTo(outboxMetrics.getInstanceId());
    }

    @Test
    @DisplayName("Should increment eventsPublished counter")
    void shouldIncrementEventsPublishedCounter() {
        // Given
        Counter counter = meterRegistry.counter("outbox.events.published");
        double initialCount = counter.count();

        // When
        outboxMetrics.recordEventsPublished("publisher1", 5);

        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 5);
    }

    @Test
    @DisplayName("Should increment processingCycles counter")
    void shouldIncrementProcessingCyclesCounter() {
        // Given
        Counter counter = meterRegistry.counter("outbox.processing.cycles");
        double initialCount = counter.count();

        // When
        outboxMetrics.recordProcessingCycle();

        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("Should increment errors counter")
    void shouldIncrementErrorsCounter() {
        // Given
        Counter counter = meterRegistry.counter("outbox.errors");
        double initialCount = counter.count();

        // When
        outboxMetrics.recordError("publisher1");

        // Then
        assertThat(counter.count()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("Should create tagged autoPause counter and increment")
    void shouldCreateTaggedAutoPauseCounter_AndIncrement() {
        // Given
        String topic = "topic1";
        String publisher = "publisher1";

        // When
        outboxMetrics.recordAutoPause(topic, publisher, 5, "Connection error");

        // Then - Verify counter exists with tags
        Counter counter = meterRegistry.find("outbox.auto_pause_total")
                .tag("topic", topic)
                .tag("publisher", publisher)
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should set isLeader gauge to 1 when true")
    void shouldSetIsLeaderGauge_To1_WhenTrue() {
        // Given
        Gauge gauge = meterRegistry.find("outbox.is_leader").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);

        // When
        outboxMetrics.setLeader(true);

        // Then
        assertThat(gauge.value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should set isLeader gauge to 0 when false")
    void shouldSetIsLeaderGauge_To0_WhenFalse() {
        // Given
        Gauge gauge = meterRegistry.find("outbox.is_leader").gauge();
        outboxMetrics.setLeader(true); // Set to 1 first
        assertThat(gauge.value()).isEqualTo(1.0);

        // When
        outboxMetrics.setLeader(false);

        // Then
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should create gauge for publisher and set leader status")
    void shouldCreateGaugeForPublisher_AndSetLeaderStatus() {
        // Given
        String publisherName = "publisher1";

        // When
        outboxMetrics.setLeaderForPublisher(publisherName, true);

        // Then - Verify gauge exists with tags
        Gauge gauge = meterRegistry.find("outbox.is_leader")
                .tag("publisher", publisherName)
                .tag("instance", outboxMetrics.getInstanceId())
                .gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should update existing publisher gauge")
    void shouldUpdateExistingPublisherGauge() {
        // Given
        String publisherName = "publisher1";
        outboxMetrics.setLeaderForPublisher(publisherName, true);
        Gauge gauge = meterRegistry.find("outbox.is_leader")
                .tag("publisher", publisherName)
                .gauge();
        assertThat(gauge.value()).isEqualTo(1.0);

        // When
        outboxMetrics.setLeaderForPublisher(publisherName, false);

        // Then
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle multiple publishers")
    void shouldHandleMultiplePublishers() {
        // When
        outboxMetrics.setLeaderForPublisher("publisher1", true);
        outboxMetrics.setLeaderForPublisher("publisher2", false);
        outboxMetrics.setLeaderForPublisher("publisher3", true);

        // Then - Verify all gauges exist
        Gauge gauge1 = meterRegistry.find("outbox.is_leader")
                .tag("publisher", "publisher1")
                .gauge();
        Gauge gauge2 = meterRegistry.find("outbox.is_leader")
                .tag("publisher", "publisher2")
                .gauge();
        Gauge gauge3 = meterRegistry.find("outbox.is_leader")
                .tag("publisher", "publisher3")
                .gauge();

        assertThat(gauge1.value()).isEqualTo(1.0);
        assertThat(gauge2.value()).isEqualTo(0.0);
        assertThat(gauge3.value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should handle empty HOSTNAME environment variable")
    void shouldHandleEmptyHOSTNAMEEnvironmentVariable() {
        // Given
        when(environment.getProperty("HOSTNAME")).thenReturn("");
        when(environment.getProperty("crablet.instance.id")).thenReturn("custom-id");

        // When
        OutboxMetrics metrics = new OutboxMetrics(environment);

        // Then - Should use custom instance id (empty HOSTNAME is treated as null)
        assertThat(metrics.getInstanceId()).isEqualTo("custom-id");
    }

    @Test
    @DisplayName("Should handle empty custom instance id")
    void shouldHandleEmptyCustomInstanceId() {
        // Given
        when(environment.getProperty("HOSTNAME")).thenReturn(null);
        when(environment.getProperty("crablet.instance.id")).thenReturn("");

        // When
        OutboxMetrics metrics = new OutboxMetrics(environment);

        // Then - Should fall back to hostname
        assertThat(metrics.getInstanceId()).isNotNull();
        assertThat(metrics.getInstanceId()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle method calls before bindTo is called")
    void shouldHandleMethodCalls_BeforeBindToIsCalled() {
        // Given - Create metrics without calling bindTo
        OutboxMetrics metrics = new OutboxMetrics(environment);

        // When & Then - Should not throw NullPointerException
        assertThatCode(() -> {
            metrics.recordProcessingCycle();
            metrics.recordEventsPublished("publisher1", 5);
            metrics.recordError("publisher1");
            metrics.recordAutoPause("topic1", "publisher1", 3, "Error");
        }).doesNotThrowAnyException();
    }
}

