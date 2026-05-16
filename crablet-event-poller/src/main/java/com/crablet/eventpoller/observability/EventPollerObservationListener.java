package com.crablet.eventpoller.observability;

import com.crablet.eventpoller.metrics.BackoffStateMetric;
import com.crablet.eventpoller.metrics.LeadershipMetric;
import com.crablet.eventpoller.metrics.ProcessingCycleMetric;
import com.crablet.observability.CrabletObservationNames;
import com.crablet.observability.CrabletObservationTags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.event.EventListener;

/**
 * Records event-poller observations from poller-owned metric events.
 */
public class EventPollerObservationListener {

    private final ObservationRegistry observationRegistry;

    public EventPollerObservationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @EventListener
    public void onLeadership(LeadershipMetric event) {
        Observation.createNotStarted(CrabletObservationNames.POLLER_LEADERSHIP, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.PROCESSOR, event.processorId())
                .lowCardinalityKeyValue(CrabletObservationTags.INSTANCE_ID, event.instanceId())
                .lowCardinalityKeyValue("leader", Boolean.toString(event.isLeader()))
                .observe(() -> { });
    }

    @EventListener
    public void onProcessingCycle(ProcessingCycleMetric event) {
        Observation.createNotStarted(CrabletObservationNames.POLLER_PROCESSING_CYCLE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.PROCESSOR, event.processorId())
                .lowCardinalityKeyValue(CrabletObservationTags.INSTANCE_ID, event.instanceId())
                .lowCardinalityKeyValue("empty", Boolean.toString(event.empty()))
                .observe(() -> { });
    }

    @EventListener
    public void onBackoffState(BackoffStateMetric event) {
        Observation.createNotStarted(CrabletObservationNames.POLLER_BACKOFF, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.PROCESSOR, event.processorId())
                .lowCardinalityKeyValue(CrabletObservationTags.INSTANCE_ID, event.instanceId())
                .lowCardinalityKeyValue("active", Boolean.toString(event.active()))
                .observe(() -> { });
    }
}
