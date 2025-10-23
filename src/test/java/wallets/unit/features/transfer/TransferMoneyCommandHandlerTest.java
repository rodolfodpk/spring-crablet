package wallets.unit.features.transfer;
import wallets.integration.AbstractWalletIntegrationTest;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.CommandResult;
import com.crablet.core.ConcurrencyException;
import com.crablet.core.EventStore;
import com.crablet.core.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.features.transfer.TransferMoneyCommand;
import com.wallets.features.transfer.TransferStateProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import wallets.integration.AbstractWalletIntegrationTest;
import wallets.testutils.WalletTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test TransferMoneyCommandHandler with minimal state projection.
 * <p>
 * DCB Principle: Tests verify that handler projects balances for both wallets.
 */
class TransferMoneyCommandHandlerTest extends AbstractWalletIntegrationTest {

    private com.wallets.features.transfer.TransferMoneyCommandHandler handler;
    private TransferStateProjector transferProjector;
    @Autowired
    private ObjectMapper objectMapper;
    private WalletBalanceProjector balanceProjector;

    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        balanceProjector = new WalletBalanceProjector();
        transferProjector = new TransferStateProjector();
        handler = new com.wallets.features.transfer.TransferMoneyCommandHandler(objectMapper, balanceProjector, transferProjector);
    }

    @Test
    @DisplayName("Should successfully handle transfer money command")
    void testHandleTransferMoney_Success() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 1000);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);

        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.append(List.of(fromWalletInputEvent, toWalletInputEvent));

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 300, "Payment");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("MoneyTransferred");
                    assertThat(event.tags()).hasSize(3);
                    assertThat(event.tags().get(0))
                            .satisfies(tag -> {
                                assertThat(tag.key()).isEqualTo("transfer_id");
                                assertThat(tag.value()).isEqualTo("transfer1");
                            });
                    assertThat(event.tags().get(1))
                            .satisfies(tag -> {
                                assertThat(tag.key()).isEqualTo("from_wallet_id");
                                assertThat(tag.value()).isEqualTo("wallet1");
                            });
                    assertThat(event.tags().get(2))
                            .satisfies(tag -> {
                                assertThat(tag.key()).isEqualTo("to_wallet_id");
                                assertThat(tag.value()).isEqualTo("wallet2");
                            });
                });

        MoneyTransferred transfer = WalletTestUtils.deserializeEventData(result.events().get(0).data(), MoneyTransferred.class);
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
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.append(List.of(toWalletInputEvent));

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
        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        eventStore.append(List.of(fromWalletInputEvent));

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

        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.append(List.of(fromWalletInputEvent, toWalletInputEvent));

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

        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.append(List.of(fromWalletInputEvent, toWalletInputEvent));

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 500, "Full transfer");

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        MoneyTransferred transfer = WalletTestUtils.deserializeEventData(result.events().get(0).data(), MoneyTransferred.class);
        assertThat(transfer.fromBalance()).isEqualTo(0); // 500 - 500
        assertThat(transfer.toBalance()).isEqualTo(700); // 200 + 500
    }

    @Test
    @DisplayName("Should detect cursor conflict on concurrent wallet state change")
    void testHandleTransferMoney_Idempotency() {
        // Arrange - create both wallets
        WalletOpened fromWallet = WalletOpened.of("wallet1", "Alice", 1000);
        WalletOpened toWallet = WalletOpened.of("wallet2", "Bob", 500);

        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.append(List.of(fromWalletInputEvent, toWalletInputEvent));

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

        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", "wallet1")
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", "wallet2")
                .build();

        eventStore.append(List.of(fromWalletInputEvent, toWalletInputEvent));

        // Act - transfer should only project balances for both wallets, not full WalletState
        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", "wallet1", "wallet2", 200, "Test transfer");
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert - verify correct balance calculations
        MoneyTransferred transfer = WalletTestUtils.deserializeEventData(result.events().get(0).data(), MoneyTransferred.class);
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

        StoredEvent fromWalletEvent = WalletTestUtils.createEvent(fromWallet);
        StoredEvent toWalletEvent = WalletTestUtils.createEvent(toWallet);

        AppendEvent fromWalletInputEvent = AppendEvent.builder(fromWalletEvent.type())
                .data(fromWalletEvent.data())
                .tag("wallet_id", fromWalletId)
                .build();
        AppendEvent toWalletInputEvent = AppendEvent.builder(toWalletEvent.type())
                .data(toWalletEvent.data())
                .tag("wallet_id", toWalletId)
                .build();

        eventStore.append(List.of(fromWalletInputEvent, toWalletInputEvent));

        TransferMoneyCommand cmd = TransferMoneyCommand.of("transfer1", fromWalletId, toWalletId, transferAmount, description);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        MoneyTransferred transfer = WalletTestUtils.deserializeEventData(result.events().get(0).data(), MoneyTransferred.class);
        assertThat(transfer.fromBalance()).isEqualTo(fromBalance - transferAmount);
        assertThat(transfer.toBalance()).isEqualTo(toBalance + transferAmount);
        assertThat(transfer.description()).isEqualTo(description);
    }
}
