package com.crablet.outbox.observability;

import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.ProcessingCycleMetric;
import com.crablet.outbox.metrics.PublishingDurationMetric;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("OutboxObservationListener")
class OutboxObservationListenerTest {

    private final OutboxObservationListener listener =
            new OutboxObservationListener(ObservationRegistry.NOOP);

    @Test
    @DisplayName("onEventsPublished records observation without error")
    void onEventsPublished() {
        assertThatNoException().isThrownBy(() ->
                listener.onEventsPublished(new EventsPublishedMetric("kafka-publisher", 5)));
    }

    @Test
    @DisplayName("onPublishingDuration records observation without error")
    void onPublishingDuration() {
        assertThatNoException().isThrownBy(() ->
                listener.onPublishingDuration(new PublishingDurationMetric("kafka-publisher", Duration.ofMillis(12))));
    }

    @Test
    @DisplayName("onOutboxError records observation without error")
    void onOutboxError() {
        assertThatNoException().isThrownBy(() ->
                listener.onOutboxError(new OutboxErrorMetric("kafka-publisher")));
    }

    @Test
    @DisplayName("onProcessingCycle records observation without error")
    void onProcessingCycle() {
        assertThatNoException().isThrownBy(() ->
                listener.onProcessingCycle(new ProcessingCycleMetric()));
    }
}
