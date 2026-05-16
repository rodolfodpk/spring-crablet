package com.crablet.views.observability;

import com.crablet.views.metrics.ViewProjectionErrorMetric;
import com.crablet.views.metrics.ViewProjectionMetric;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("ViewObservationListener")
class ViewObservationListenerTest {

    private final ViewObservationListener listener =
            new ViewObservationListener(ObservationRegistry.NOOP);

    @Test
    @DisplayName("onViewProjection records observation without error")
    void onViewProjection() {
        assertThatNoException().isThrownBy(() ->
                listener.onViewProjection(new ViewProjectionMetric("WalletSummary", 10, Duration.ofMillis(5))));
    }

    @Test
    @DisplayName("onViewProjectionError records observation without error")
    void onViewProjectionError() {
        assertThatNoException().isThrownBy(() ->
                listener.onViewProjectionError(new ViewProjectionErrorMetric("WalletSummary")));
    }
}
