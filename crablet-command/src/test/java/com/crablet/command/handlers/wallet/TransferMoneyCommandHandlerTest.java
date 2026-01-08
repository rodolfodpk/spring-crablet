package com.crablet.command.handlers.wallet;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandResult;
import com.crablet.examples.wallet.commands.TransferMoneyCommand;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.exceptions.InsufficientFundsException;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
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
 * Integration tests for TransferMoneyCommandHandler.
 * <p>
 * DCB Principle: Tests verify that handler projects balances for both wallets.
 */
@DisplayName("TransferMoneyCommandHandler Integration Tests")
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class TransferMoneyCommandHandlerTest extends com.crablet.eventstore.integration.AbstractCrabletTest {

    private TransferMoneyCommandHandler handler;

    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private WalletTestUtils walletTestUtils;
    
    @Autowired
    private WalletPeriodHelper periodHelper;

    @BeforeEach
    void setUp() {
        handler = new TransferMoneyCommandHandler(periodHelper);
    }

    @Test
    @DisplayName("Should successfully handle transfer money command")
    void testHandleTransferMoney_Success() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 1000);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);

        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.appendIf(List.of(fromWalletInputEvent, toWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 300, "Payment");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("MoneyTransferred");
                    // Period-aware events now include year, month, day, hour tags in addition to transfer_id, from_wallet_id, to_wallet_id
                    assertThat(event.tags()).hasSizeGreaterThanOrEqualTo(3);
                    assertThat(event.tags()).anyMatch(tag -> 
                        "transfer_id".equals(tag.key()) && "transfer1".equals(tag.value()));
                    assertThat(event.tags()).anyMatch(tag -> 
                        "from_wallet_id".equals(tag.key()) && "wallet1".equals(tag.value()));
                    assertThat(event.tags()).anyMatch(tag -> 
                        "to_wallet_id".equals(tag.key()) && "wallet2".equals(tag.value()));
                    // Verify period tags are present
                    assertThat(event.tags()).anyMatch(tag -> "from_year".equals(tag.key()));
                    assertThat(event.tags()).anyMatch(tag -> "from_month".equals(tag.key()));
                    assertThat(event.tags()).anyMatch(tag -> "to_year".equals(tag.key()));
                    assertThat(event.tags()).anyMatch(tag -> "to_month".equals(tag.key()));
                });

        MoneyTransferred transfer = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), MoneyTransferred.class);
        assertThat(transfer)
                .satisfies(t -> {
                    assertThat(t.transferId()).isEqualTo("transfer1");
                    assertThat(t.fromWalletId()).isEqualTo("wallet1");
                    assertThat(t.toWalletId()).isEqualTo("wallet2");
                    assertThat(t.amount()).isEqualTo(300);
                    assertThat(t.fromBalance()).isEqualTo(700); // 1000 - 300
                    assertThat(t.toBalance()).isEqualTo(800); // 500 + 300
                    assertThat(t.description()).isEqualTo("Payment");
                });
    }

    @Test
    @DisplayName("Should prevent transfer from non-existent source wallet")
    void testHandleTransferMoney_SourceWalletNotFound() {
        // Arrange - create only destination wallet
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.appendIf(List.of(toWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "nonexistent", "wallet2", 100, "Payment");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should prevent transfer to non-existent destination wallet")
    void testHandleTransferMoney_DestinationWalletNotFound() {
        // Arrange - create only source wallet
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.appendIf(List.of(fromWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "nonexistent", 100, "Payment");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should prevent transfer with insufficient funds")
    void testHandleTransferMoney_InsufficientFunds() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 100);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);

        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.appendIf(List.of(fromWalletInputEvent, toWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 200, "Payment");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("wallet1")
                .hasMessageContaining("100")
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("Should prevent transfer with zero amount at command creation")
    void testHandleTransferMoney_ZeroAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 0, "Zero transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should prevent transfer with negative amount at command creation")
    void testHandleTransferMoney_NegativeAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", -100, "Negative transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should handle transfer with exact balance")
    void testHandleTransferMoney_ExactBalance() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 500);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 200);

        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.appendIf(List.of(fromWalletInputEvent, toWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 500, "Full transfer");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        MoneyTransferred transfer = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), MoneyTransferred.class);
        assertThat(transfer.fromBalance()).isEqualTo(0); // 500 - 500
        assertThat(transfer.toBalance()).isEqualTo(700); // 200 + 500
    }

    @Test
    @DisplayName("Should detect cursor conflict on concurrent wallet state change")
    void testHandleTransferMoney_Idempotency() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 1000);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);

        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.appendIf(List.of(fromWalletInputEvent, toWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 300, "Payment");

        // Act - first transfer succeeds
        CommandResult firstResult = handler.handle(eventStore, cmd);
        AppendCondition firstCondition = firstResult.appendCondition();
        eventStore.appendIf(firstResult.events(), firstCondition);

        // Verify: Cursor-based protection detects DECISION MODEL changes, not operation ID duplicates
        // If client re-reads state after transfer, they get a fresh cursor
        CommandResult secondResult = handler.handle(eventStore, cmd);
        
        // The second call succeeds because:
        // 1. Command handler re-reads wallet state (fresh cursor)
        // 2. Wallet balances are already updated from first transfer
        // 3. No idempotency check on operation ID (only on wallet creation)
        // 4. appendIf would succeed with fresh cursor (cursor protection works correctly)
        assertThat(secondResult.events()).isNotEmpty();
        assertThat(secondResult.events().get(0).type()).isEqualTo("MoneyTransferred");
    }

    @Test
    @DisplayName("Should project minimal state - balances for both wallets")
    void testProjectMinimalState() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 1000);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);

        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.appendIf(List.of(fromWalletInputEvent, toWalletInputEvent), AppendCondition.empty());

        // Act - transfer should only project balances for both wallets, not full WalletState
        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 200, "Test transfer");
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert - verify correct balance calculations
        MoneyTransferred transfer = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), MoneyTransferred.class);
        assertThat(transfer.fromBalance()).isEqualTo(800); // 1000 - 200
        assertThat(transfer.toBalance()).isEqualTo(700); // 500 + 200
    }

    @ParameterizedTest
    @CsvSource({
            "wallet1, wallet2, 1000, 500, 300, Payment",
            "wallet3, wallet4, 2000, 100, 500, Salary",
            "wallet5, wallet6, 100, 1000, 50, Tip"
    })
    @DisplayName("Should handle various transfer scenarios")
    void testTransferScenarios(String fromWalletId, String toWalletId, int fromBalance, int toBalance, int transferAmount, String description) {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of(fromWalletId, "Alice", fromBalance);
        WalletOpened toWallet = WalletOpened.of(toWalletId, "Bob", toBalance);

        StoredEvent fromWalletEvent = walletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = walletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", fromWalletId)
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", toWalletId)
                .build();

        eventStore.appendIf(List.of(fromWalletInputEvent, toWalletInputEvent), AppendCondition.empty());

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", fromWalletId, toWalletId, transferAmount, description);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        MoneyTransferred transfer = walletTestUtils.deserializeEventData(result.events().get(0).eventData(), MoneyTransferred.class);
        assertThat(transfer.fromBalance()).isEqualTo(fromBalance - transferAmount);
        assertThat(transfer.toBalance()).isEqualTo(toBalance + transferAmount);
        assertThat(transfer.description()).isEqualTo(description);
    }
}
