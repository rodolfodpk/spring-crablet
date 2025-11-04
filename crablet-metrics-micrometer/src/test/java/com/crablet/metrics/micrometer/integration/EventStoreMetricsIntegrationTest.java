package com.crablet.metrics.micrometer.integration;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventStore metrics via MicrometerMetricsCollector.
 * Verifies that EventStore operations publish metric events that are collected by MicrometerMetricsCollector
 * and recorded to Micrometer.
 */
@DisplayName("EventStore Metrics Integration Tests")
class EventStoreMetricsIntegrationTest extends AbstractMetricsIntegrationTest {

    @Test
    @DisplayName("Should collect events.appended metric via Spring Events")
    void shouldCollectEventsAppendedMetric() {
        // Given: verify initial state
        Counter eventsCounter = meterRegistry.find("eventstore.events.appended").counter();
        double initialCount = eventsCounter != null ? eventsCounter.count() : 0.0;

        // When: append events (EventStoreImpl publishes EventsAppendedMetric via Spring Events)
        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet1")
                        .data(WalletOpened.of("wallet1", "Alice", 1000))
                        .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // Then: MicrometerMetricsCollector should have recorded the metric
        Counter finalCounter = meterRegistry.find("eventstore.events.appended").counter();
        assertThat(finalCounter).isNotNull();
        assertThat(finalCounter.count()).isEqualTo(initialCount + 1.0);
    }

    @Test
    @DisplayName("Should collect event type metrics via Spring Events")
    void shouldCollectEventTypeMetrics() {
        // When: append events with different types
        eventStore.appendIf(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet2")
                        .data(WalletOpened.of("wallet2", "Bob", 1000))
                        .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet2")
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", "wallet2", 500, 1500, "Test"))
                        .build()
        ), AppendCondition.empty());

        // Then: event type metrics should be recorded
        Counter walletOpenedCounter = meterRegistry.find("eventstore.events.by_type")
            .tag("event_type", "WalletOpened")
            .counter();
        assertThat(walletOpenedCounter).isNotNull();
        assertThat(walletOpenedCounter.count()).isGreaterThanOrEqualTo(1.0);
        
        Counter depositMadeCounter = meterRegistry.find("eventstore.events.by_type")
            .tag("event_type", "DepositMade")
            .counter();
        assertThat(depositMadeCounter).isNotNull();
        assertThat(depositMadeCounter.count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Should collect metrics for multiple events in batch")
    void shouldCollectMetricsForMultipleEvents() {
        // Given: verify initial state
        Counter eventsCounter = meterRegistry.find("eventstore.events.appended").counter();
        double initialCount = eventsCounter != null ? eventsCounter.count() : 0.0;

        // When: append multiple events in one batch
        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet3")
                        .data(WalletOpened.of("wallet3", "Charlie", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet3")
                        .tag("deposit_id", "deposit2")
                        .data(DepositMade.of("deposit2", "wallet3", 200, 1200, "Batch"))
                        .build()
        );
        eventStore.appendIf(events, AppendCondition.empty());

        // Then: events.appended should reflect the batch size
        Counter finalCounter = meterRegistry.find("eventstore.events.appended").counter();
        assertThat(finalCounter).isNotNull();
        assertThat(finalCounter.count()).isEqualTo(initialCount + 2.0);
    }
}

