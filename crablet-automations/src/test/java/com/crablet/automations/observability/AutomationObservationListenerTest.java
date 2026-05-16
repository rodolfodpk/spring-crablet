package com.crablet.automations.observability;

import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("AutomationObservationListener")
class AutomationObservationListenerTest {

    private final AutomationObservationListener listener =
            new AutomationObservationListener(ObservationRegistry.NOOP);

    @Test
    @DisplayName("onAutomationExecution records observation without error")
    void onAutomationExecution() {
        assertThatNoException().isThrownBy(() ->
                listener.onAutomationExecution(new AutomationExecutionMetric("TransferFunds", 3, Duration.ofMillis(8))));
    }

    @Test
    @DisplayName("onAutomationExecutionError records observation without error")
    void onAutomationExecutionError() {
        assertThatNoException().isThrownBy(() ->
                listener.onAutomationExecutionError(new AutomationExecutionErrorMetric("TransferFunds")));
    }
}
