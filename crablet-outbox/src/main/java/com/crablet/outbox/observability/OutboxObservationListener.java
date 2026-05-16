package com.crablet.outbox.observability;

import com.crablet.observability.CrabletObservationNames;
import com.crablet.observability.CrabletObservationTags;
import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.PublishingDurationMetric;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.event.EventListener;

/**
 * Records outbox observations from outbox-owned metric events.
 */
public class OutboxObservationListener {

    private final ObservationRegistry observationRegistry;

    public OutboxObservationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @EventListener
    public void onEventsPublished(EventsPublishedMetric event) {
        Observation.createNotStarted(CrabletObservationNames.OUTBOX_PUBLISH, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.PUBLISHER, event.publisherName())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "success")
                .observe(() -> { });
    }

    @EventListener
    public void onPublishingDuration(PublishingDurationMetric event) {
        Observation.createNotStarted(CrabletObservationNames.OUTBOX_PUBLISH, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.PUBLISHER, event.publisherName())
                .observe(() -> { });
    }

    @EventListener
    public void onOutboxError(OutboxErrorMetric event) {
        Observation.createNotStarted(CrabletObservationNames.OUTBOX_PUBLISH, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.PUBLISHER, event.publisherName())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "failure")
                .observe(() -> { });
    }

    @EventListener
    public void onProcessingCycle(com.crablet.outbox.metrics.ProcessingCycleMetric event) {
        Observation.createNotStarted(CrabletObservationNames.OUTBOX_PROCESSING_CYCLE, observationRegistry)
                .observe(() -> { });
    }
}
