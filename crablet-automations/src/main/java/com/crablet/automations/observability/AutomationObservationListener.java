package com.crablet.automations.observability;

import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.observability.CrabletObservationNames;
import com.crablet.observability.CrabletObservationTags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.event.EventListener;

/**
 * Records automation observations from automation-owned metric events.
 */
public class AutomationObservationListener {

    private final ObservationRegistry observationRegistry;

    public AutomationObservationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @EventListener
    public void onAutomationExecution(AutomationExecutionMetric event) {
        Observation.createNotStarted(CrabletObservationNames.AUTOMATION_DECIDE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.AUTOMATION, event.automationName())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "success")
                .observe(() -> { });
    }

    @EventListener
    public void onAutomationExecutionError(AutomationExecutionErrorMetric event) {
        Observation.createNotStarted(CrabletObservationNames.AUTOMATION_DECIDE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.AUTOMATION, event.automationName())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "failure")
                .observe(() -> { });
    }
}
