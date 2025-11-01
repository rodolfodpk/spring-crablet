package com.crablet.examples.wallet.features.deposit;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.command.CommandResult;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import com.crablet.examples.wallet.domain.exception.WalletNotFoundException;
import com.crablet.examples.wallet.domain.projections.WalletBalanceProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.examples.wallet.testutils.WalletTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DepositCommandHandler.
 * <p>
 * DCB Principle: Tests verify that handler projects only balance + existence.
 */
@DisplayName("DepositCommandHandler Integration Tests")
class DepositCommandHandlerTest extends com.crablet.eventstore.integration.AbstractCrabletTest {

    private DepositCommandHandler handler;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        handler = new DepositCommandHandler();
    }

    @Test
    @DisplayName("Should successfully handle deposit command")
    void testHandleDeposit_Success() {
        // Arrange - create wallet first
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent walletEvent = WalletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.append(List.of(walletInputEvent));

        DepositCommand cmd = DepositCommand.of("deposit1", "wallet1", 500, "Bonus payment");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("DepositMade");
                    assertThat(event.tags()).hasSize(2);
                    assertThat(event.tags().get(0))
                            .satisfies(tag -> {
                                assertThat(tag.key()).isEqualTo("wallet_id");
                                assertThat(tag.value()).isEqualTo("wallet1");
                            });
                    assertThat(event.tags().get(1))
                            .satisfies(tag -> {
                                assertThat(tag.key()).isEqualTo("deposit_id");
                                assertThat(tag.value()).isEqualTo("deposit1");
                            });
                });

        DepositMade deposit = WalletTestUtils.deserializeEventData(result.events().get(0).eventData(), DepositMade.class);
        assertThat(deposit)
                .satisfies(d -> {
                    assertThat(d.depositId()).isEqualTo("deposit1");
                    assertThat(d.walletId()).isEqualTo("wallet1");
                    assertThat(d.amount()).isEqualTo(500);
                    assertThat(d.newBalance()).isEqualTo(1500); // 1000 + 500
                    assertThat(d.description()).isEqualTo("Bonus payment");
                });
    }

    @Test
    @DisplayName("Should throw exception when wallet does not exist")
    void testHandleDeposit_WalletNotFound() {
        // Arrange
        DepositCommand cmd = DepositCommand.of("deposit1", "nonexistent", 500, "Bonus payment");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should throw exception for zero amount at command creation")
    void testHandleDeposit_ZeroAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> DepositCommand.of("deposit1", "wallet1", 0, "Zero deposit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should throw exception for negative amount at command creation")
    void testHandleDeposit_NegativeAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> DepositCommand.of("deposit1", "wallet1", -100, "Negative deposit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should project minimal state - balance + existence")
    void testProjectMinimalState() {
        // Arrange - create wallet with multiple events
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent walletEvent = WalletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.append(List.of(walletInputEvent));

        // Act - deposit should only project balance + existence, not full WalletState
        DepositCommand cmd = DepositCommand.of("deposit1", "wallet1", 200, "Test deposit");
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert - verify correct new balance calculation
        DepositMade deposit = WalletTestUtils.deserializeEventData(result.events().get(0).eventData(), DepositMade.class);
        assertThat(deposit.newBalance()).isEqualTo(1200); // 1000 + 200
    }

    @ParameterizedTest
    @CsvSource({
            "wallet1, Alice, 1000, Bonus payment",
            "wallet2, Bob, 0, Initial deposit",
            "wallet3, Charlie, 5000, Salary"
    })
    @DisplayName("Should handle various deposit scenarios")
    void testDepositScenarios(String walletId, String owner, int initialBalance, String description) {
        // Arrange - create wallet
        WalletOpened walletOpened = WalletOpened.of(walletId, owner, initialBalance);
        StoredEvent walletEvent = WalletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", walletId)
                .build();
        eventStore.append(List.of(walletInputEvent));

        DepositCommand cmd = DepositCommand.of("deposit1", walletId, 100, description);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        DepositMade deposit = WalletTestUtils.deserializeEventData(result.events().get(0).eventData(), DepositMade.class);
        assertThat(deposit.newBalance()).isEqualTo(initialBalance + 100);
        assertThat(deposit.description()).isEqualTo(description);
    }
}

