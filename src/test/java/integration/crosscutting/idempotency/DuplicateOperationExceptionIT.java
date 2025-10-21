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
 * Integration test for cursor-based concurrency control.
 * <p>
 * Tests that duplicate operations are handled correctly:
 * - Wallet creation duplicates throw ConcurrencyException (idempotency check)
 * - Other operation duplicates succeed (client re-reads state, gets fresh cursor)
 * - No advisory locks needed for operations (cursor protects against duplicates)
 */
class DuplicateOperationExceptionIT extends AbstractCrabletTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Test
    @DisplayName("Should trigger ConcurrencyException for duplicate wallet creation")
    void shouldTriggerDuplicateOperationExceptionForDuplicateWalletCreation() {
        String walletId = "duplicate-wallet-" + System.currentTimeMillis();
        OpenWalletCommand command = OpenWalletCommand.of(walletId, "Alice", 1000);

        // First creation should succeed
        ExecutionResult firstResult = commandExecutor.executeCommand(command);
        assertThat(firstResult.wasCreated()).isTrue();

        // Second creation should trigger ConcurrencyException (wallet already exists)
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("duplicate operation detected");
    }

    @Test
    @DisplayName("Should handle duplicate deposit (no longer idempotent)")
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

        // Second deposit also succeeds (client re-reads state, gets fresh cursor)
        // Note: Operations are no longer idempotent by operation ID
        ExecutionResult secondResult = commandExecutor.executeCommand(depositCommand);
        assertThat(secondResult.wasCreated()).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate withdrawal (no longer idempotent)")
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

        // Second withdrawal also succeeds (client re-reads state, gets fresh cursor)
        ExecutionResult secondResult = commandExecutor.executeCommand(withdrawCommand);
        assertThat(secondResult.wasCreated()).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate transfer (no longer idempotent)")
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

        // Second transfer also succeeds (client re-reads state, gets fresh cursor)
        ExecutionResult secondResult = commandExecutor.executeCommand(transferCommand);
        assertThat(secondResult.wasCreated()).isTrue();
    }

    @Test
    @DisplayName("Should handle all operation types consistently (no idempotency)")
    void shouldHandleAllOperationTypesConsistently() {
        String wallet1Id = "consistency-1-" + System.currentTimeMillis();
        String wallet2Id = "consistency-2-" + System.currentTimeMillis();
        String operationId = "operation-" + System.currentTimeMillis();

        // Create wallets
        OpenWalletCommand openCommand1 = OpenWalletCommand.of(wallet1Id, "Frank", 1000);
        OpenWalletCommand openCommand2 = OpenWalletCommand.of(wallet2Id, "Grace", 500);
        commandExecutor.executeCommand(openCommand1);
        commandExecutor.executeCommand(openCommand2);

        // Test deposit duplicate - both succeed
        DepositCommand depositCommand = DepositCommand.of(operationId, wallet1Id, 100, "Test");
        ExecutionResult depositFirst = commandExecutor.executeCommand(depositCommand);
        ExecutionResult depositSecond = commandExecutor.executeCommand(depositCommand);
        assertThat(depositFirst.wasCreated()).isTrue();
        assertThat(depositSecond.wasCreated()).isTrue();

        // Test withdrawal duplicate - both succeed
        String withdrawalId = "withdrawal-" + System.currentTimeMillis();
        WithdrawCommand withdrawCommand = WithdrawCommand.of(withdrawalId, wallet1Id, 50, "Test");
        ExecutionResult withdrawFirst = commandExecutor.executeCommand(withdrawCommand);
        ExecutionResult withdrawSecond = commandExecutor.executeCommand(withdrawCommand);
        assertThat(withdrawFirst.wasCreated()).isTrue();
        assertThat(withdrawSecond.wasCreated()).isTrue();

        // Test transfer duplicate - both succeed
        String transferId = "transfer-" + System.currentTimeMillis();
        TransferMoneyCommand transferCommand = TransferMoneyCommand.of(
                transferId, wallet1Id, wallet2Id, 75, "Test"
        );
        ExecutionResult transferFirst = commandExecutor.executeCommand(transferCommand);
        ExecutionResult transferSecond = commandExecutor.executeCommand(transferCommand);
        assertThat(transferFirst.wasCreated()).isTrue();
        assertThat(transferSecond.wasCreated()).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate operations consistently")
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

        // Second deposit also succeeds (no longer idempotent)
        ExecutionResult secondResult = commandExecutor.executeCommand(depositCommand);
        assertThat(secondResult.wasCreated()).isTrue();
    }
}
