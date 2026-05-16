package com.crablet.eventpoller.observability;

import com.crablet.eventpoller.metrics.BackoffStateMetric;
import com.crablet.eventpoller.metrics.LeadershipMetric;
import com.crablet.eventpoller.metrics.ProcessingCycleMetric;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("EventPollerObservationListener")
class EventPollerObservationListenerTest {

    private final EventPollerObservationListener listener =
            new EventPollerObservationListener(ObservationRegistry.NOOP);

    @Test
    @DisplayName("onLeadership records observation without error")
    void onLeadership() {
        assertThatNoException().isThrownBy(() ->
                listener.onLeadership(new LeadershipMetric("views", "instance-1", true)));
    }

    @Test
    @DisplayName("onProcessingCycle records observation without error")
    void onProcessingCycle() {
        assertThatNoException().isThrownBy(() ->
                listener.onProcessingCycle(new ProcessingCycleMetric("views", "instance-1", 5, false)));
    }

    @Test
    @DisplayName("onBackoffState records observation without error")
    void onBackoffState() {
        assertThatNoException().isThrownBy(() ->
                listener.onBackoffState(new BackoffStateMetric("views", "instance-1", true, 3)));
    }
}
