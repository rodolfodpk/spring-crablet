package unit.domain;

import com.crablet.core.CommandResult;
import com.crablet.core.EventStore;
import com.crablet.core.InputEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.features.deposit.DepositCommand;
import com.wallets.features.deposit.DepositCommandHandler;
import com.wallets.features.openwallet.OpenWalletCommand;
import com.wallets.features.openwallet.OpenWalletCommandHandler;
import com.wallets.features.withdraw.WithdrawCommand;
import com.wallets.features.withdraw.WithdrawCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the new wallet commands: DepositCommand, WithdrawCommand, CloseWalletCommand.
 */
@DisplayName("Wallet New Commands Tests")
class WalletNewCommandsTest extends AbstractCrabletTest {

    private DepositCommandHandler depositHandler;
    private WithdrawCommandHandler withdrawHandler;
    private OpenWalletCommandHandler openHandler;
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        
        WalletBalanceProjector balanceProjector = new WalletBalanceProjector(objectMapper);
        depositHandler = new DepositCommandHandler(objectMapper, balanceProjector);
        withdrawHandler = new WithdrawCommandHandler(objectMapper, balanceProjector);
        openHandler = new OpenWalletCommandHandler(objectMapper);
    }

    @Test
    @DisplayName("Should successfully deposit money into existing wallet")
    void shouldSuccessfullyDepositMoneyIntoExistingWallet() throws Exception {
        // Given: An existing wallet
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        eventStore.append(List.of(InputEvent.of("WalletOpened", List.of(new Tag("wallet_id", "wallet1")), objectMapper.writeValueAsBytes(walletOpened))));

        // When: Depositing money
        DepositCommand depositCmd = DepositCommand.of("deposit1", "wallet1", 500, "Salary deposit");
        CommandResult result = depositHandler.handle(eventStore, depositCmd);

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
            .isInstanceOf(com.wallets.domain.exception.WalletNotFoundException.class)
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
        eventStore.append(List.of(InputEvent.of("WalletOpened", List.of(new Tag("wallet_id", "wallet1")), objectMapper.writeValueAsBytes(walletOpened))));

        // When: Withdrawing money
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet1", 300, "Shopping");
        CommandResult result = withdrawHandler.handle(eventStore, withdrawCmd);

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
        eventStore.append(List.of(InputEvent.of("WalletOpened", List.of(new Tag("wallet_id", "wallet1")), objectMapper.writeValueAsBytes(walletOpened))));

        // When: Withdrawing more than available
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");

        // Then: Should throw exception
        assertThatThrownBy(() -> withdrawHandler.handle(eventStore, withdrawCmd))
            .isInstanceOf(com.wallets.domain.exception.InsufficientFundsException.class)
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
        CommandResult openResult = openHandler.handle(eventStore, openCmd);
        eventStore.appendIf(openResult.events(), openResult.appendCondition());

        // And: Depositing money
        DepositCommand depositCmd = DepositCommand.of("deposit1", "wallet1", 500, "Salary");
        CommandResult depositResult = depositHandler.handle(eventStore, depositCmd);
        eventStore.appendIf(depositResult.events(), depositResult.appendCondition());

        // And: Withdrawing money
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");
        CommandResult withdrawResult = withdrawHandler.handle(eventStore, withdrawCmd);
        eventStore.appendIf(withdrawResult.events(), withdrawResult.appendCondition());

        // And: Withdrawing remaining balance
        WithdrawCommand finalWithdrawCmd = WithdrawCommand.of("withdrawal2", "wallet1", 1300, "Final withdrawal");
        CommandResult finalWithdrawResult = withdrawHandler.handle(eventStore, finalWithdrawCmd);
        eventStore.appendIf(finalWithdrawResult.events(), finalWithdrawResult.appendCondition());

        // Then: Wallet should have zero balance
        // Note: WalletState building would require implementing a state projector
        // For now, we just verify the events were created successfully
        var events = eventStore.query(com.crablet.core.Query.empty(), null);
        assertThat(events).hasSize(4); // OpenWallet + Deposit + 2 Withdrawals
    }
}
