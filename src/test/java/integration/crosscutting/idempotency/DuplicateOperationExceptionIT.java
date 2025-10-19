package integration.crosscutting.idempotency;

import com.crablet.core.CommandExecutor;
import com.crablet.core.ConcurrencyException;
import com.crablet.core.ExecutionResult;
import com.wallets.features.deposit.DepositCommand;
import com.wallets.features.openwallet.OpenWalletCommand;
import com.wallets.features.transfer.TransferMoneyCommand;
import com.wallets.features.withdraw.WithdrawCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for idempotency handling.
 * <p>
 * Tests that duplicate operations are handled idempotently:
 * - Wallet creation duplicates throw ConcurrencyException (AppendCondition violated)
 * - Other operation duplicates return idempotent result (200 OK)
 */
class DuplicateOperationExceptionIT extends AbstractCrabletTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Test
    @DisplayName("Should trigger DuplicateOperationException for duplicate wallet creation")
    void shouldTriggerDuplicateOperationExceptionForDuplicateWalletCreation() {
        String walletId = "duplicate-wallet-" + System.currentTimeMillis();
        OpenWalletCommand command = OpenWalletCommand.of(walletId, "Alice", 1000);

        // First creation should succeed
        commandExecutor.executeCommand(command);

        // Second creation should trigger ConcurrencyException which maps to DuplicateOperationException
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("AppendCondition violated");
    }

    @Test
    @DisplayName("Should handle duplicate deposit idempotently")
    void shouldTriggerDuplicateOperationExceptionForDuplicateDeposit() {
        String walletId = "duplicate-deposit-" + System.currentTimeMillis();
        String depositId = "deposit-" + System.currentTimeMillis();

        // Create wallet first
        OpenWalletCommand openCommand = OpenWalletCommand.of(walletId, "Bob", 500);
        commandExecutor.executeCommand(openCommand);

        DepositCommand depositCommand = DepositCommand.of(depositId, walletId, 200, "Test deposit");

        // First deposit should succeed (returns CREATED)
        ExecutionResult firstResult = commandExecutor.executeCommand(depositCommand);
        assertThat(firstResult.wasCreated()).isTrue();

        // Second deposit should be idempotent (returns OK)
        ExecutionResult secondResult = commandExecutor.executeCommand(depositCommand);
        assertThat(secondResult.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate withdrawal idempotently")
    void shouldTriggerDuplicateOperationExceptionForDuplicateWithdrawal() {
        String walletId = "duplicate-withdrawal-" + System.currentTimeMillis();
        String withdrawalId = "withdrawal-" + System.currentTimeMillis();

        // Create wallet first
        OpenWalletCommand openCommand = OpenWalletCommand.of(walletId, "Charlie", 1000);
        commandExecutor.executeCommand(openCommand);

        WithdrawCommand withdrawCommand = WithdrawCommand.of(withdrawalId, walletId, 300, "Test withdrawal");

        // First withdrawal should succeed (returns CREATED)
        ExecutionResult firstResult = commandExecutor.executeCommand(withdrawCommand);
        assertThat(firstResult.wasCreated()).isTrue();

        // Second withdrawal should be idempotent (returns OK)
        ExecutionResult secondResult = commandExecutor.executeCommand(withdrawCommand);
        assertThat(secondResult.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate transfer idempotently")
    void shouldTriggerDuplicateOperationExceptionForDuplicateTransfer() {
        String wallet1Id = "duplicate-transfer-1-" + System.currentTimeMillis();
        String wallet2Id = "duplicate-transfer-2-" + System.currentTimeMillis();
        String transferId = "transfer-" + System.currentTimeMillis();

        // Create both wallets
        OpenWalletCommand openCommand1 = OpenWalletCommand.of(wallet1Id, "David", 1000);
        OpenWalletCommand openCommand2 = OpenWalletCommand.of(wallet2Id, "Eve", 500);
        commandExecutor.executeCommand(openCommand1);
        commandExecutor.executeCommand(openCommand2);

        TransferMoneyCommand transferCommand = TransferMoneyCommand.of(
                transferId, wallet1Id, wallet2Id, 200, "Test transfer"
        );

        // First transfer should succeed (returns CREATED)
        ExecutionResult firstResult = commandExecutor.executeCommand(transferCommand);
        assertThat(firstResult.wasCreated()).isTrue();

        // Second transfer should be idempotent (returns OK)
        ExecutionResult secondResult = commandExecutor.executeCommand(transferCommand);
        assertThat(secondResult.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Should handle all operation types consistently")
    void shouldHandleAllOperationTypesConsistently() {
        String wallet1Id = "consistency-1-" + System.currentTimeMillis();
        String wallet2Id = "consistency-2-" + System.currentTimeMillis();
        String operationId = "operation-" + System.currentTimeMillis();

        // Create wallets
        OpenWalletCommand openCommand1 = OpenWalletCommand.of(wallet1Id, "Frank", 1000);
        OpenWalletCommand openCommand2 = OpenWalletCommand.of(wallet2Id, "Grace", 500);
        commandExecutor.executeCommand(openCommand1);
        commandExecutor.executeCommand(openCommand2);

        // Test deposit duplicate
        DepositCommand depositCommand = DepositCommand.of(operationId, wallet1Id, 100, "Test");
        ExecutionResult depositFirst = commandExecutor.executeCommand(depositCommand);
        ExecutionResult depositSecond = commandExecutor.executeCommand(depositCommand);
        assertThat(depositFirst.wasCreated()).isTrue();
        assertThat(depositSecond.wasIdempotent()).isTrue();

        // Test withdrawal duplicate (different operation ID)
        String withdrawalId = "withdrawal-" + System.currentTimeMillis();
        WithdrawCommand withdrawCommand = WithdrawCommand.of(withdrawalId, wallet1Id, 50, "Test");
        ExecutionResult withdrawFirst = commandExecutor.executeCommand(withdrawCommand);
        ExecutionResult withdrawSecond = commandExecutor.executeCommand(withdrawCommand);
        assertThat(withdrawFirst.wasCreated()).isTrue();
        assertThat(withdrawSecond.wasIdempotent()).isTrue();

        // Test transfer duplicate (different operation ID)
        String transferId = "transfer-" + System.currentTimeMillis();
        TransferMoneyCommand transferCommand = TransferMoneyCommand.of(
                transferId, wallet1Id, wallet2Id, 75, "Test"
        );
        ExecutionResult transferFirst = commandExecutor.executeCommand(transferCommand);
        ExecutionResult transferSecond = commandExecutor.executeCommand(transferCommand);
        assertThat(transferFirst.wasCreated()).isTrue();
        assertThat(transferSecond.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Should preserve operation context in idempotent result")
    void shouldPreserveOperationContextInException() {
        String walletId = "context-" + System.currentTimeMillis();
        String depositId = "deposit-context-" + System.currentTimeMillis();

        // Create wallet
        OpenWalletCommand openCommand = OpenWalletCommand.of(walletId, "Henry", 500);
        commandExecutor.executeCommand(openCommand);

        DepositCommand depositCommand = DepositCommand.of(depositId, walletId, 100, "Context test");

        // First deposit should succeed
        ExecutionResult firstResult = commandExecutor.executeCommand(depositCommand);
        assertThat(firstResult.wasCreated()).isTrue();

        // Second deposit should be idempotent
        ExecutionResult secondResult = commandExecutor.executeCommand(depositCommand);
        assertThat(secondResult.wasIdempotent()).isTrue();
    }
}
