package com.crablet.eventstore.integration;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStoreMetrics;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventStore metrics instrumentation.
 * Tests library-agnostic metrics recording for EventStore operations.
 * Only tests EventStore-level metrics (not command framework metrics).
 * 
 * Note: Metrics are recorded manually in tests since EventStore.append() doesn't
 * automatically record metrics (metrics are recorded by CommandExecutor in production).
 */
@DisplayName("EventStore Metrics Integration Tests")
class EventStoreMetricsTest extends AbstractCrabletTest {

    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private EventStoreMetrics eventStoreMetrics;

    @Test
    @DisplayName("Should record eventstore.events.appended metric for wallet events")
    void shouldRecordEventsAppendedMetric() {
        // Given: get events appended counter (library metric)
        Counter eventsAppendedCounter = meterRegistry.find("eventstore.events.appended").counter();
        assertThat(eventsAppendedCounter).isNotNull();

        // When: append wallet events using EventStore API and record metrics
        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet1")
                        .data(WalletOpened.of("wallet1", "Alice", 1000))
                        .build()
        );
        eventStore.append(events);
        eventStoreMetrics.recordEventsAppended(events.size());

        // Then: eventstore.events.appended should be recorded (library metric verification)
        double count = eventsAppendedCounter.count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should record eventstore.events.appended metric for multiple events")
    void shouldRecordEventsAppendedMetricForMultipleEvents() {
        // Given: verify initial state
        Counter eventsCounter = meterRegistry.find("eventstore.events.appended").counter();
        double initialCount = eventsCounter.count();

        // When: append multiple events using EventStore API and record metrics
        List<AppendEvent> events1 = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet2")
                        .data(WalletOpened.of("wallet2", "Bob", 1000))
                        .build()
        );
        eventStore.append(events1);
        eventStoreMetrics.recordEventsAppended(events1.size());
        
        List<AppendEvent> events2 = List.of(
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet2")
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", "wallet2", 500, 1500, "Direct append"))
                        .build()
        );
        eventStore.append(events2);
        eventStoreMetrics.recordEventsAppended(events2.size());

        // Then: metrics should reflect all operations (library-level tracking)
        double finalCount = eventsCounter.count();
        assertThat(finalCount).isGreaterThan(initialCount);
        assertThat(finalCount - initialCount).isGreaterThanOrEqualTo(2); // At least 2 events
    }

    @Test
    @DisplayName("Should verify eventstore metrics are library-agnostic")
    void shouldVerifyMetricsAreLibraryAgnostic() {
        // When: perform EventStore operations using direct API
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet3")
                        .data(WalletOpened.of("wallet3", "Charlie", 1000))
                        .build()
        ));
        
        eventStore.append(List.of(
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet3")
                        .tag("deposit_id", "deposit2")
                        .data(DepositMade.of("deposit2", "wallet3", 300, 1300, "Metrics verification"))
                        .build()
        ));

        // Then: verify library-level metrics (not domain-specific)
        assertThat(meterRegistry.find("eventstore.events.appended").counter()).isNotNull();
        assertThat(meterRegistry.find("eventstore.concurrency.violations").counter()).isNotNull();
    }

    @Test
    @DisplayName("Should record eventstore.events.appended metric for various event types")
    void shouldRecordEventsAppendedMetricForVariousEventTypes() {
        // Given: get events appended counter
        Counter eventsAppendedCounter = meterRegistry.find("eventstore.events.appended").counter();
        double initialCount = eventsAppendedCounter.count();

        // When: append multiple events of different types and record metrics
        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet4")
                        .data(WalletOpened.of("wallet4", "Diana", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet4")
                        .tag("deposit_id", "deposit3")
                        .data(DepositMade.of("deposit3", "wallet4", 200, 1200, "Batch append"))
                        .build()
        );
        eventStore.append(events);
        eventStoreMetrics.recordEventsAppended(events.size());

        // Then: eventstore.events.appended should be incremented
        double finalCount = eventsAppendedCounter.count();
        assertThat(finalCount).isGreaterThan(initialCount);
        assertThat(finalCount - initialCount).isGreaterThanOrEqualTo(2);
    }
}

