package com.crablet.command.handlers.wallet;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandResult;
import com.crablet.examples.wallet.features.withdraw.WithdrawCommand;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import com.crablet.examples.wallet.domain.event.WithdrawalMade;
import com.crablet.examples.wallet.domain.exception.InsufficientFundsException;
import com.crablet.examples.wallet.domain.exception.WalletNotFoundException;
import com.crablet.examples.wallet.domain.period.WalletPeriodHelper;
import com.crablet.command.handlers.wallet.WalletTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for WithdrawCommandHandler.
 * <p>
 * DCB Principle: Tests verify that handler projects only balance + existence.
 */
@DisplayName("WithdrawCommandHandler Integration Tests")
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class WithdrawCommandHandlerTest extends com.crablet.eventstore.integration.AbstractCrabletTest {

    private WithdrawCommandHandler handler;

    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private WalletTestUtils walletTestUtils;
    
    @Autowired
    private WalletPeriodHelper periodHelper;

    @BeforeEach
    void setUp() {
        handler = new WithdrawCommandHandler(periodHelper);
    }

    @Test
    @DisplayName("Should successfully handle withdrawal command")
    void testHandleWithdraw_Success() {
        // Arrange - create wallet first
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent walletEvent = walletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.appendIf(List.of(walletInputEvent), AppendCondition.empty());

        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 300, "Shopping");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("WithdrawalMade");
                    // Period-aware events now include year, month, day, hour tags in addition to wallet_id and withdrawal_id
                    assertThat(event.tags()).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(event.tags()).anyMatch(tag -> 
                        "wallet_id".equals(tag.key()) && "wallet1".equals(tag.value()));
                    assertThat(event.tags()).anyMatch(tag -> 
                        "withdrawal_id".equals(tag.key()) && "withdrawal1".equals(tag.value()));
                    // Verify period tags are present
                    assertThat(event.tags()).anyMatch(tag -> "year".equals(tag.key()));
                    assertThat(event.tags()).anyMatch(tag -> "month".equals(tag.key()));
                });

        WithdrawalMade withdrawal = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), WithdrawalMade.class);
        assertThat(withdrawal)
                .satisfies(w -> {
                    assertThat(w.withdrawalId()).isEqualTo("withdrawal1");
                    assertThat(w.walletId()).isEqualTo("wallet1");
                    assertThat(w.amount()).isEqualTo(300);
                    assertThat(w.newBalance()).isEqualTo(700); // 1000 - 300
                    assertThat(w.description()).isEqualTo("Shopping");
                });
    }

    @Test
    @DisplayName("Should throw exception when wallet does not exist")
    void testHandleWithdraw_WalletNotFound() {
        // Arrange
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "nonexistent", 100, "Test withdrawal");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should throw exception for insufficient funds")
    void testHandleWithdraw_InsufficientFunds() {
        // Arrange - create wallet with low balance
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 100);
        StoredEvent walletEvent = walletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.appendIf(List.of(walletInputEvent), AppendCondition.empty());

        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Overdraft");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("wallet1")
                .hasMessageContaining("100")
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("Should throw exception for zero amount at command creation")
    void testHandleWithdraw_ZeroAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> WithdrawCommand.of("withdrawal1", "wallet1", 0, "Zero withdrawal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should throw exception for negative amount at command creation")
    void testHandleWithdraw_NegativeAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> WithdrawCommand.of("withdrawal1", "wallet1", -100, "Negative withdrawal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should handle withdrawal with exact balance")
    void testHandleWithdraw_ExactBalance() {
        // Arrange - create wallet with exact balance
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 500);
        StoredEvent walletEvent = walletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.appendIf(List.of(walletInputEvent), AppendCondition.empty());

        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 500, "Full withdrawal");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        WithdrawalMade withdrawal = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), WithdrawalMade.class);
        assertThat(withdrawal.newBalance()).isEqualTo(0); // 500 - 500
    }

    @Test
    @DisplayName("Should project minimal state - balance + existence")
    void testProjectMinimalState() {
        // Arrange - create wallet with multiple events
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent walletEvent = walletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.appendIf(List.of(walletInputEvent), AppendCondition.empty());

        // Act - withdrawal should only project balance + existence, not full WalletState
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Test withdrawal");
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert - verify correct new balance calculation
        WithdrawalMade withdrawal = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), WithdrawalMade.class);
        assertThat(withdrawal.newBalance()).isEqualTo(800); // 1000 - 200
    }

    @ParameterizedTest
    @CsvSource({
            "wallet1, Alice, 1000, Shopping",
            "wallet2, Bob, 500, Rent",
            "wallet3, Charlie, 2000, Vacation"
    })
    @DisplayName("Should handle various withdrawal scenarios")
    void testWithdrawalScenarios(String walletId, String owner, int initialBalance, String description) {
        // Arrange - create wallet
        WalletOpened walletOpened = WalletOpened.of(walletId, owner, initialBalance);
        StoredEvent walletEvent = walletTestUtils.createEvent(walletOpened);
        AppendEvent walletInputEvent = AppendEvent.builder(walletEvent.type())
                .data(walletEvent.data())
                .tag("wallet_id", walletId)
                .build();
        eventStore.appendIf(List.of(walletInputEvent), AppendCondition.empty());

        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", walletId, 100, description);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        WithdrawalMade withdrawal = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), WithdrawalMade.class);
        assertThat(withdrawal.newBalance()).isEqualTo(initialBalance - 100);
        assertThat(withdrawal.description()).isEqualTo(description);
    }
}
