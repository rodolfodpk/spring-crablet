package com.crablet.command.observability;

import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import com.crablet.observability.CrabletObservationNames;
import com.crablet.observability.CrabletObservationTags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.event.EventListener;

/**
 * Records command observations from command-owned metric events.
 */
public class CommandObservationListener {

    private final ObservationRegistry observationRegistry;

    public CommandObservationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @EventListener
    public void onCommandSuccess(CommandSuccessMetric event) {
        Observation.createNotStarted(CrabletObservationNames.COMMAND_HANDLE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.COMMAND_TYPE, event.commandType())
                .lowCardinalityKeyValue(CrabletObservationTags.OPERATION_TYPE, event.operationType())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "success")
                .observe(() -> { });
    }

    @EventListener
    public void onCommandFailure(CommandFailureMetric event) {
        Observation.createNotStarted(CrabletObservationNames.COMMAND_HANDLE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.COMMAND_TYPE, event.commandType())
                .lowCardinalityKeyValue(CrabletObservationTags.ERROR_TYPE, event.errorType())
                .lowCardinalityKeyValue(CrabletObservationTags.OUTCOME, "failure")
                .observe(() -> { });
    }

    @EventListener
    public void onIdempotentOperation(IdempotentOperationMetric event) {
        Observation.createNotStarted(CrabletObservationNames.COMMAND_IDEMPOTENT_DUPLICATE, observationRegistry)
                .lowCardinalityKeyValue(CrabletObservationTags.COMMAND_TYPE, event.commandType())
                .observe(() -> { });
    }
}
