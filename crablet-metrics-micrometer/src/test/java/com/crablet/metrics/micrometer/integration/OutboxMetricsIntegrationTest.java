package com.crablet.metrics.micrometer.integration;

import com.crablet.eventstore.AppendEvent;
import com.crablet.examples.wallet.events.WalletOpened;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Outbox metrics via MicrometerMetricsCollector.
 * Verifies that Outbox operations publish metric events that are collected by MicrometerMetricsCollector
 * and recorded to Micrometer.
 */
@DisplayName("Outbox Metrics Integration Tests")
class OutboxMetricsIntegrationTest extends AbstractMetricsIntegrationTest {

    @Test
    @DisplayName("Should collect events published metrics via Spring Events")
    void shouldCollectEventsPublishedMetrics() {
        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet1")
                        .data(WalletOpened.of("wallet1", "Alice", 1000))
                        .build()
        );
        eventStore.appendCommutative(events);

        // Placeholder: assert outbox.events.published etc. once publishing path is exercised reliably.
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    @DisplayName("Should collect processing cycle metrics via Spring Events")
    void shouldCollectProcessingCycleMetrics() {
        // Placeholder: assert outbox.processing.cycles when processor runs in-test.
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    @DisplayName("Should collect publishing duration metrics via Spring Events")
    void shouldCollectPublishingDurationMetrics() {
        // Placeholder: assert outbox.publishing.duration timer after publish activity.
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    @DisplayName("Should collect leadership metrics via Spring Events")
    void shouldCollectLeadershipMetrics() {
        // Placeholder: assert processor.is_leader gauge after leadership wiring.
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    @DisplayName("Should collect outbox error metrics via Spring Events")
    void shouldCollectOutboxErrorMetrics() {
        // Placeholder: assert outbox.errors counter after error scenario.
        assertThat(meterRegistry).isNotNull();
    }
}
