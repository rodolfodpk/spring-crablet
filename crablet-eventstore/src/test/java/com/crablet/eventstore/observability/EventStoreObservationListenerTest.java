package com.crablet.eventstore.observability;

import com.crablet.eventstore.metrics.ConcurrencyViolationMetric;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("EventStoreObservationListener")
class EventStoreObservationListenerTest {

    private final EventStoreObservationListener listener =
            new EventStoreObservationListener(ObservationRegistry.NOOP);

    @Test
    @DisplayName("onEventsAppended records observation without error")
    void onEventsAppended() {
        assertThatNoException().isThrownBy(() -> listener.onEventsAppended(new EventsAppendedMetric(3)));
    }

    @Test
    @DisplayName("onEventType records observation without error")
    void onEventType() {
        assertThatNoException().isThrownBy(() -> listener.onEventType(new EventTypeMetric("WalletCreated")));
    }

    @Test
    @DisplayName("onConcurrencyViolation records observation without error")
    void onConcurrencyViolation() {
        assertThatNoException().isThrownBy(() -> listener.onConcurrencyViolation(new ConcurrencyViolationMetric()));
    }
}
