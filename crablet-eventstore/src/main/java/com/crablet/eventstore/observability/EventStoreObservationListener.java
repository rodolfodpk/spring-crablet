package com.crablet.eventstore.observability;

import com.crablet.eventstore.metrics.ConcurrencyViolationMetric;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import com.crablet.observability.CrabletObservationNames;
import com.crablet.observability.CrabletObservationTags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.event.EventListener;

/**
 * Records EventStore observations from EventStore-owned metric events.
 */
public class EventStoreObservationListener {

    private final ObservationRegistry observationRegistry;

    public EventStoreObservationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @EventListener
    public void onEventsAppended(EventsAppendedMetric event) {
        Observation.createNotStarted(CrabletObservationNames.EVENTSTORE_APPEND, observationRegistry)
                .lowCardinalityKeyValue("event.count", Integer.toString(event.count()))
                .observe(() -> { });
    }

    @EventListener
    public void onEventType(EventTypeMetric event) {
        Observation.createNotStarted(CrabletObservationNames.EVENTSTORE_EVENT_TYPE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.EVENT_TYPE, event.eventType())
                .observe(() -> { });
    }

    @EventListener
    public void onConcurrencyViolation(ConcurrencyViolationMetric event) {
        Observation.createNotStarted(CrabletObservationNames.EVENTSTORE_CONCURRENCY_VIOLATION, observationRegistry)
                .observe(() -> { });
    }
}
