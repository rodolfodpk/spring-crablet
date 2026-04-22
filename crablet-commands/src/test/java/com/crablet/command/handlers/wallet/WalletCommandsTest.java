package com.crablet.command.handlers.wallet;

import com.crablet.command.CommandDecision;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.Tag;
import com.crablet.examples.wallet.commands.DepositCommand;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.examples.wallet.commands.WithdrawCommand;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
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
 * Integration tests for wallet commands: DepositCommand, WithdrawCommand.
 */
@DisplayName("Wallet Commands Integration Tests")
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class WalletCommandsTest extends com.crablet.test.AbstractCrabletTest {

    private com.crablet.examples.wallet.commands.DepositCommandHandler depositHandler;
    private com.crablet.examples.wallet.commands.WithdrawCommandHandler withdrawHandler;
    private com.crablet.examples.wallet.commands.OpenWalletCommandHandler openHandler;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventRepository testHelper;

    @Autowired
    private WalletPeriodHelper periodHelper;

    @BeforeEach
    void setUp() {
        depositHandler = new com.crablet.examples.wallet.commands.DepositCommandHandler(periodHelper);
        withdrawHandler = new com.crablet.examples.wallet.commands.WithdrawCommandHandler(periodHelper);
        openHandler = new com.crablet.examples.wallet.commands.OpenWalletCommandHandler();
    }

    /** Dispatch a CommandDecision to the appropriate EventStore semantic method. */
    private void appendResult(CommandDecision result) {
        switch (result) {
            case CommandDecision.Commutative c -> eventStore.appendCommutative(c.events());
            case CommandDecision.CommutativeGuarded cg -> eventStore.appendCommutative(cg.events());
            case CommandDecision.NonCommutative nc ->
                eventStore.appendNonCommutative(nc.events(), nc.decisionModel(), nc.streamPosition());
            case CommandDecision.Idempotent i ->
                eventStore.appendIdempotent(i.events(), i.eventType(), i.tagKey(), i.tagValue());
            case CommandDecision.NoOp e -> {} // no-op
        }
    }

    @Test
    @DisplayName("Should successfully deposit money into existing wallet")
    void shouldSuccessfullyDepositMoneyIntoExistingWallet() throws Exception {
        // Given: An existing wallet
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        eventStore.appendCommutative(List.of(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet1")
                .data(walletOpened)
                .build()));

        // When: Depositing money
        DepositCommand depositCmd = DepositCommand.of("deposit1", "wallet1", 500, "Salary deposit");
        CommandDecision result = depositHandler.handle(eventStore, depositCmd);

        // Then: Should create DepositMade event
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).type()).isEqualTo("DepositMade");
        assertThat(result.events().get(0).tags()).contains(
                new Tag("wallet_id", "wallet1")
        );
    }

    @Test
    @DisplayName("Should fail to deposit money into non-existent wallet")
    void shouldFailToDepositMoneyIntoNonExistentWallet() throws Exception {
        // When: Depositing money into non-existent wallet
        DepositCommand depositCmd = DepositCommand.of("deposit1", "nonexistent", 500, "Salary deposit");

        // Then: Should throw exception
        assertThatThrownBy(() -> depositHandler.handle(eventStore, depositCmd))
                .isInstanceOf(com.crablet.examples.wallet.exceptions.WalletNotFoundException.class)
                .hasMessage("Wallet not found: nonexistent");
    }

    @Test
    @DisplayName("Should fail to deposit negative amount at command creation")
    void shouldFailToDepositNegativeAmount() throws Exception {
        // When: Trying to create command with negative amount
        // Then: Should throw exception at command creation
        assertThatThrownBy(() -> DepositCommand.of("deposit1", "wallet1", -100, "Invalid deposit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should successfully withdraw money from existing wallet")
    void shouldSuccessfullyWithdrawMoneyFromExistingWallet() throws Exception {
        // Given: An existing wallet with sufficient balance
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        eventStore.appendCommutative(List.of(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet1")
                .data(walletOpened)
                .build()));

        // When: Withdrawing money
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet1", 300, "Shopping");
        CommandDecision result = withdrawHandler.handle(eventStore, withdrawCmd);

        // Then: Should create WithdrawalMade event
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).type()).isEqualTo("WithdrawalMade");
        assertThat(result.events().get(0).tags()).contains(
                new Tag("wallet_id", "wallet1")
        );
    }

    @Test
    @DisplayName("Should fail to withdraw more than available balance")
    void shouldFailToWithdrawMoreThanAvailableBalance() throws Exception {
        // Given: An existing wallet with limited balance
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 100);
        eventStore.appendCommutative(List.of(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet1")
                .data(walletOpened)
                .build()));

        // When: Withdrawing more than available
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");

        // Then: Should throw exception
        assertThatThrownBy(() -> withdrawHandler.handle(eventStore, withdrawCmd))
                .isInstanceOf(com.crablet.examples.wallet.exceptions.InsufficientFundsException.class)
                .hasMessage("Insufficient funds in wallet wallet1: balance 100, requested 200");
    }


    @ParameterizedTest
    @CsvSource({
            "deposit1, wallet1, 0, 'Zero amount deposit'",
            "deposit1, wallet1, -100, 'Negative amount deposit'"
    })
    @DisplayName("Should validate deposit command parameters at creation")
    void shouldValidateDepositCommandParameters(String depositId, String walletId, int amount, String description) throws Exception {
        // When: Creating deposit command with invalid parameters
        // Then: Should throw exception at command creation
        assertThatThrownBy(() -> DepositCommand.of(depositId, walletId, amount, description))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "withdrawal1, wallet1, 0, 'Zero amount withdrawal'",
            "withdrawal1, wallet1, -100, 'Negative amount withdrawal'"
    })
    @DisplayName("Should validate withdraw command parameters at creation")
    void shouldValidateWithdrawCommandParameters(String withdrawalId, String walletId, int amount, String description) throws Exception {
        // When: Creating withdraw command with invalid parameters
        // Then: Should throw exception at command creation
        assertThatThrownBy(() -> WithdrawCommand.of(withdrawalId, walletId, amount, description))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle complete wallet lifecycle")
    void shouldHandleCompleteWalletLifecycle() throws Exception {
        // Given: An empty event store

        // When: Opening a wallet
        OpenWalletCommand openCmd = OpenWalletCommand.of("wallet1", "Alice", 1000);
        appendResult(openHandler.handle(eventStore, openCmd));

        // And: Depositing money
        DepositCommand depositCmd = DepositCommand.of("deposit1", "wallet1", 500, "Salary");
        appendResult(depositHandler.handle(eventStore, depositCmd));

        // And: Withdrawing money
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");
        appendResult(withdrawHandler.handle(eventStore, withdrawCmd));

        // And: Withdrawing remaining balance
        WithdrawCommand finalWithdrawCmd = WithdrawCommand.of("withdrawal2", "wallet1", 1300, "Final withdrawal");
        appendResult(withdrawHandler.handle(eventStore, finalWithdrawCmd));

        // Then: Wallet should have zero balance
        // Note: Period helper creates statement events, so we filter to wallet lifecycle events only
        var allEvents = testHelper.query(com.crablet.eventstore.query.Query.empty(), null);
        var walletLifecycleEvents = allEvents.stream()
                .filter(e -> !e.type().startsWith("WalletStatement"))
                .toList();
        assertThat(walletLifecycleEvents).hasSize(4); // OpenWallet + Deposit + 2 Withdrawals
    }
}
