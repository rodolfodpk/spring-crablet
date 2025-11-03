package com.crablet.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OutboxPublisherMetrics.
 * Tests timer and counter creation/usage for publishers.
 */
@DisplayName("OutboxPublisherMetrics Unit Tests")
class OutboxPublisherMetricsTest {

    private MeterRegistry meterRegistry;
    private OutboxPublisherMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new OutboxPublisherMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should start publishing timer sample")
    void shouldStartPublishingTimerSample() {
        // When
        Timer.Sample sample = metrics.startPublishing("publisher1");

        // Then
        assertThat(sample).isNotNull();
    }

    @Test
    @DisplayName("Should record publishing success with timer and counter")
    void shouldRecordPublishingSuccess_WithTimerAndCounter() {
        // Given
        Timer.Sample sample = metrics.startPublishing("publisher1");
        int eventCount = 5;

        // When
        metrics.recordPublishingSuccess("publisher1", sample, eventCount);

        // Then - Verify timer exists
        Timer timer = meterRegistry.find("outbox.publishing.duration")
                .tag("publisher", "publisher1")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        // Then - Verify counter exists
        Counter counter = meterRegistry.find("outbox.events.published.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(eventCount);
    }

    @Test
    @DisplayName("Should record publishing error with counter")
    void shouldRecordPublishingError_WithCounter() {
        // When
        metrics.recordPublishingError("publisher1");

        // Then - Verify error counter exists
        Counter counter = meterRegistry.find("outbox.errors.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should handle multiple publishers separately")
    void shouldHandleMultiplePublishers_Separately() {
        // Given
        Timer.Sample sample1 = metrics.startPublishing("publisher1");
        Timer.Sample sample2 = metrics.startPublishing("publisher2");

        // When
        metrics.recordPublishingSuccess("publisher1", sample1, 3);
        metrics.recordPublishingSuccess("publisher2", sample2, 7);
        metrics.recordPublishingError("publisher1");

        // Then - Verify separate timers
        Timer timer1 = meterRegistry.find("outbox.publishing.duration")
                .tag("publisher", "publisher1")
                .timer();
        Timer timer2 = meterRegistry.find("outbox.publishing.duration")
                .tag("publisher", "publisher2")
                .timer();
        assertThat(timer1.count()).isEqualTo(1);
        assertThat(timer2.count()).isEqualTo(1);

        // Then - Verify separate counters
        Counter counter1 = meterRegistry.find("outbox.events.published.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        Counter counter2 = meterRegistry.find("outbox.events.published.by_publisher")
                .tag("publisher", "publisher2")
                .counter();
        assertThat(counter1.count()).isEqualTo(3.0);
        assertThat(counter2.count()).isEqualTo(7.0);

        // Then - Verify separate error counters
        Counter errorCounter1 = meterRegistry.find("outbox.errors.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(errorCounter1).isNotNull();
        assertThat(errorCounter1.count()).isEqualTo(1.0);
        
        // Publisher2 has no errors, so counter doesn't exist yet
        Counter errorCounter2 = meterRegistry.find("outbox.errors.by_publisher")
                .tag("publisher", "publisher2")
                .counter();
        assertThat(errorCounter2).isNull(); // Counter not created until first error
    }

    @Test
    @DisplayName("Should reuse existing timer for same publisher")
    void shouldReuseExistingTimer_ForSamePublisher() {
        // Given
        Timer.Sample sample1 = metrics.startPublishing("publisher1");
        metrics.recordPublishingSuccess("publisher1", sample1, 5);

        // When - Another publishing operation
        Timer.Sample sample2 = metrics.startPublishing("publisher1");
        metrics.recordPublishingSuccess("publisher1", sample2, 3);

        // Then - Same timer, incremented count
        Timer timer = meterRegistry.find("outbox.publishing.duration")
                .tag("publisher", "publisher1")
                .timer();
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should reuse existing counter for same publisher")
    void shouldReuseExistingCounter_ForSamePublisher() {
        // Given
        Timer.Sample sample1 = metrics.startPublishing("publisher1");
        metrics.recordPublishingSuccess("publisher1", sample1, 5);

        // When - Another publishing operation
        Timer.Sample sample2 = metrics.startPublishing("publisher1");
        metrics.recordPublishingSuccess("publisher1", sample2, 3);

        // Then - Same counter, accumulated count
        Counter counter = meterRegistry.find("outbox.events.published.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(counter.count()).isEqualTo(8.0); // 5 + 3
    }

    @Test
    @DisplayName("Should reuse existing error counter for same publisher")
    void shouldReuseExistingErrorCounter_ForSamePublisher() {
        // Given
        metrics.recordPublishingError("publisher1");

        // When - Another error
        metrics.recordPublishingError("publisher1");

        // Then - Same counter, incremented
        Counter counter = meterRegistry.find("outbox.errors.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should handle zero event count")
    void shouldHandleZeroEventCount() {
        // Given
        Timer.Sample sample = metrics.startPublishing("publisher1");

        // When
        metrics.recordPublishingSuccess("publisher1", sample, 0);

        // Then - Counter should still exist
        Counter counter = meterRegistry.find("outbox.events.published.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle large event counts")
    void shouldHandleLargeEventCounts() {
        // Given
        Timer.Sample sample = metrics.startPublishing("publisher1");

        // When
        metrics.recordPublishingSuccess("publisher1", sample, 10000);

        // Then
        Counter counter = meterRegistry.find("outbox.events.published.by_publisher")
                .tag("publisher", "publisher1")
                .counter();
        assertThat(counter.count()).isEqualTo(10000.0);
    }

    @Test
    @DisplayName("Should measure timer duration correctly")
    void shouldMeasureTimerDuration_Correctly() throws InterruptedException {
        // Given
        Timer.Sample sample = metrics.startPublishing("publisher1");

        // When - Simulate some processing time
        Thread.sleep(10);
        metrics.recordPublishingSuccess("publisher1", sample, 1);

        // Then - Timer should have recorded duration
        Timer timer = meterRegistry.find("outbox.publishing.duration")
                .tag("publisher", "publisher1")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
}

