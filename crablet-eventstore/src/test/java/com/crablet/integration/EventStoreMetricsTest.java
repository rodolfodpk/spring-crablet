package com.crablet.integration;

import com.crablet.eventstore.commands.CommandExecutor;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.wallet.domain.event.DepositMade;
import com.crablet.wallet.domain.event.WalletOpened;
import com.crablet.wallet.features.deposit.DepositCommand;
import com.crablet.wallet.features.openwallet.OpenWalletCommand;
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
 */
@DisplayName("EventStore Metrics Integration Tests")
class EventStoreMetricsTest extends AbstractCrabletTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CommandExecutor commandExecutor;

    @Test
    @DisplayName("Should record eventstore.events.appended metric for wallet events")
    void shouldRecordEventsAppendedMetric() {
        // Given: get events appended counter (library metric)
        Counter eventsAppendedCounter = meterRegistry.find("eventstore.events.appended").counter();
        assertThat(eventsAppendedCounter).isNotNull();

        // When: append wallet events using EventStore API
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet1")
                        .data(WalletOpened.of("wallet1", "Alice", 1000))
                        .build()
        ));

        // Then: eventstore.events.appended should be recorded (library metric verification)
        double count = eventsAppendedCounter.count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should record eventstore.commands.total metric for wallet commands")
    void shouldRecordCommandsTotalMetric() {
        // Given: get command counter for a specific command type (library metric)
        String commandType = "open_wallet";
        
        // When: execute wallet command via CommandExecutor
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet2", "Bob", 1000));

        // Then: eventstore.commands.total should be recorded for this command type (library metric)
        Counter commandCounter = meterRegistry.find("eventstore.commands.total")
                .tag("command_type", commandType)
                .counter();
        assertThat(commandCounter).isNotNull();
        assertThat(commandCounter.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should record eventstore.commands.duration metric for wallet commands")
    void shouldRecordCommandsDurationMetric() {
        // Given: command type
        String commandType = "deposit";
        
        // Setup wallet first
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet3", "Charlie", 1000));

        // When: execute wallet command via CommandExecutor
        commandExecutor.executeCommand(DepositCommand.of("deposit1", "wallet3", 500, "Metrics test"));

        // Then: eventstore.commands.duration should be recorded (library metric)
        // Timer metric verification - ensure timing was recorded
        var timers = meterRegistry.find("eventstore.commands.duration")
                .tag("command_type", commandType)
                .timers();
        assertThat(timers).isNotEmpty();
    }

    @Test
    @DisplayName("Should verify eventstore metrics are library-agnostic")
    void shouldVerifyMetricsAreLibraryAgnostic() {
        // When: perform various EventStore operations
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet4", "Diana", 1000));
        
        eventStore.append(List.of(
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet4")
                        .tag("deposit_id", "deposit2")
                        .data(DepositMade.of("deposit2", "wallet4", 300, 1300, "Metrics verification"))
                        .build()
        ));

        // Then: verify library-level metrics (not domain-specific)
        assertThat(meterRegistry.find("eventstore.events.appended").counter()).isNotNull();
        assertThat(meterRegistry.find("eventstore.commands.total").counters()).isNotEmpty();
        assertThat(meterRegistry.find("eventstore.concurrency.violations").counter()).isNotNull();
    }

    @Test
    @DisplayName("Should record metrics for multiple EventStore operations")
    void shouldRecordMetricsForMultipleOperations() {
        // Given: verify initial state
        Counter eventsCounter = meterRegistry.find("eventstore.events.appended").counter();
        double initialCount = eventsCounter.count();

        // When: perform multiple EventStore operations
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet5", "Eve", 1000));
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet6", "Frank", 500));
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet7")
                        .data(WalletOpened.of("wallet7", "Grace", 750))
                        .build()
        ));

        // Then: metrics should reflect all operations (library-level tracking)
        double finalCount = eventsCounter.count();
        assertThat(finalCount).isGreaterThan(initialCount);
        assertThat(finalCount - initialCount).isGreaterThanOrEqualTo(2); // At least 2 events
    }
}

