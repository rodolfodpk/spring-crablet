package com.crablet.command.observability;

import com.crablet.command.metrics.CommandFailureMetric;
import com.crablet.command.metrics.CommandSuccessMetric;
import com.crablet.command.metrics.IdempotentOperationMetric;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("CommandObservationListener")
class CommandObservationListenerTest {

    private final CommandObservationListener listener =
            new CommandObservationListener(ObservationRegistry.NOOP);

    @Test
    @DisplayName("onCommandSuccess records observation without error")
    void onCommandSuccess() {
        assertThatNoException().isThrownBy(() ->
                listener.onCommandSuccess(new CommandSuccessMetric("CreateWallet", Duration.ofMillis(10), "non_commutative")));
    }

    @Test
    @DisplayName("onCommandFailure records observation without error")
    void onCommandFailure() {
        assertThatNoException().isThrownBy(() ->
                listener.onCommandFailure(new CommandFailureMetric("CreateWallet", "concurrency")));
    }

    @Test
    @DisplayName("onIdempotentOperation records observation without error")
    void onIdempotentOperation() {
        assertThatNoException().isThrownBy(() ->
                listener.onIdempotentOperation(new IdempotentOperationMetric("CreateWallet")));
    }
}
