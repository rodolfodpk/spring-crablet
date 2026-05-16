package com.crablet.views.observability;

import com.crablet.observability.CrabletObservationNames;
import com.crablet.observability.CrabletObservationTags;
import com.crablet.views.metrics.ViewProjectionErrorMetric;
import com.crablet.views.metrics.ViewProjectionMetric;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.event.EventListener;

/**
 * Records view observations from view-owned metric events.
 */
public class ViewObservationListener {

    private final ObservationRegistry observationRegistry;

    public ViewObservationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @EventListener
    public void onViewProjection(ViewProjectionMetric event) {
        Observation.createNotStarted(CrabletObservationNames.VIEW_PROJECT, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.VIEW, event.viewName())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "success")
                .observe(() -> { });
    }

    @EventListener
    public void onViewProjectionError(ViewProjectionErrorMetric event) {
        Observation.createNotStarted(CrabletObservationNames.VIEW_PROJECT, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.VIEW, event.viewName())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "failure")
                .observe(() -> { });
    }
}
