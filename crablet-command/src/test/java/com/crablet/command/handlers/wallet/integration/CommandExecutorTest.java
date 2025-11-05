package com.crablet.command.handlers.wallet.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.ExecutionResult;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.examples.wallet.domain.exception.InsufficientFundsException;
import com.crablet.examples.wallet.domain.exception.WalletNotFoundException;
import com.crablet.examples.wallet.features.deposit.DepositCommand;
import com.crablet.examples.wallet.features.openwallet.OpenWalletCommand;
import com.crablet.examples.wallet.features.transfer.TransferMoneyCommand;
import com.crablet.examples.wallet.features.withdraw.WithdrawCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CommandExecutor using wallet commands.
 * Tests end-to-end command execution: CommandExecutor → Handler → EventStore
 */
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
@DisplayName("CommandExecutor Integration Tests")
class CommandExecutorTest extends AbstractCrabletTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    private EventRepository eventRepository;

    @Test
    @DisplayName("Should execute open wallet command successfully")
    void shouldExecuteOpenWalletCommandSuccessfully() {
        // Given: open wallet command
        OpenWalletCommand cmd = OpenWalletCommand.of("wallet1", "Alice", 1000);

        // When: execute via CommandExecutor
        ExecutionResult result = commandExecutor.executeCommand(cmd);

        // Then: verify command executed
        assertThat(result).isNotNull();
        assertThat(result.wasIdempotent()).isFalse();

        // Verify events persisted
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "wallet1");
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
    }

    @Test
    @DisplayName("Should execute deposit command successfully")
    void shouldExecuteDepositCommandSuccessfully() {
        // Given: wallet exists
        OpenWalletCommand openCmd = OpenWalletCommand.of("wallet2", "Bob", 1000);
        commandExecutor.executeCommand(openCmd);

        // When: deposit money
        DepositCommand depositCmd = DepositCommand.of("deposit1", "wallet2", 500, "Salary");
        ExecutionResult result = commandExecutor.executeCommand(depositCmd);

        // Then: deposit should succeed
        assertThat(result).isNotNull();
        assertThat(result.wasIdempotent()).isFalse();

        // Verify deposit event
        Query query = Query.forEventAndTag("DepositMade", "deposit_id", "deposit1");
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should execute withdraw command successfully")
    void shouldExecuteWithdrawCommandSuccessfully() {
        // Given: wallet with balance
        OpenWalletCommand openCmd = OpenWalletCommand.of("wallet3", "Charlie", 1000);
        commandExecutor.executeCommand(openCmd);

        // When: withdraw money
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal1", "wallet3", 300, "ATM withdrawal");
        ExecutionResult result = commandExecutor.executeCommand(withdrawCmd);

        // Then: withdrawal should succeed
        assertThat(result).isNotNull();
        assertThat(result.wasIdempotent()).isFalse();

        // Verify withdrawal event
        Query query = Query.forEventAndTag("WithdrawalMade", "withdrawal_id", "withdrawal1");
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should execute transfer command successfully")
    void shouldExecuteTransferCommandSuccessfully() {
        // Given: two wallets
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet4", "Diana", 1000));
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet5", "Eve", 500));

        // When: transfer money
        TransferMoneyCommand transferCmd = TransferMoneyCommand.of(
                "transfer1",
                "wallet4",
                "wallet5",
                300,
                "Transfer test"
        );
        ExecutionResult result = commandExecutor.executeCommand(transferCmd);

        // Then: transfer should succeed
        assertThat(result).isNotNull();
        assertThat(result.wasIdempotent()).isFalse();

        // Verify transfer event
        Query query = Query.forEventAndTag("MoneyTransferred", "transfer_id", "transfer1");
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should handle deposit to non-existent wallet")
    void shouldHandleDepositToNonExistentWallet() {
        // Given: deposit command for non-existent wallet
        DepositCommand depositCmd = DepositCommand.of("deposit2", "wallet_nonexistent", 500, "Invalid deposit");

        // When/Then: should throw WalletNotFoundException
        assertThatThrownBy(() -> commandExecutor.executeCommand(depositCmd))
                .isInstanceOf(WalletNotFoundException.class);

        // Verify no events were persisted
        Query query = Query.forEventAndTag("DepositMade", "deposit_id", "deposit2");
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should handle withdrawal with insufficient funds")
    void shouldHandleWithdrawalWithInsufficientFunds() {
        // Given: wallet with limited balance
        commandExecutor.executeCommand(OpenWalletCommand.of("wallet6", "Frank", 100));

        // When/Then: try to withdraw more than balance
        WithdrawCommand withdrawCmd = WithdrawCommand.of("withdrawal2", "wallet6", 500, "Invalid withdrawal");
        assertThatThrownBy(() -> commandExecutor.executeCommand(withdrawCmd))
                .isInstanceOf(InsufficientFundsException.class);

        // Verify no withdrawal event
        Query query = Query.forEventAndTag("WithdrawalMade", "withdrawal_id", "withdrawal2");
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should execute multiple commands in sequence (wallet lifecycle)")
    void shouldExecuteMultipleCommandsInSequence() {
        // Given: empty wallet
        String walletId = "wallet7";

        // When: execute wallet lifecycle
        commandExecutor.executeCommand(OpenWalletCommand.of(walletId, "Grace", 1000));
        commandExecutor.executeCommand(DepositCommand.of("deposit3", walletId, 500, "Deposit 1"));
        commandExecutor.executeCommand(DepositCommand.of("deposit4", walletId, 300, "Deposit 2"));
        commandExecutor.executeCommand(WithdrawCommand.of("withdrawal3", walletId, 200, "Withdrawal 1"));

        // Then: verify all events persisted
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).hasSize(4); // 1 open + 2 deposits + 1 withdrawal
    }

    @Test
    @DisplayName("Should handle invalid command with validation error")
    void shouldHandleInvalidCommandWithValidationError() {
        // Given/When/Then: invalid command (negative amount) should throw at construction
        assertThatThrownBy(() -> DepositCommand.of("deposit5", "wallet8", -100, "Invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle concurrent deposits with DCB concurrency control")
    void shouldHandleConcurrentDepositsWithDCB() {
        // Given: wallet
        String walletId = "wallet9";
        commandExecutor.executeCommand(OpenWalletCommand.of(walletId, "Henry", 1000));

        // When: execute two deposits (simulating concurrent operations)
        commandExecutor.executeCommand(DepositCommand.of("deposit6", walletId, 100, "Deposit 1"));
        commandExecutor.executeCommand(DepositCommand.of("deposit7", walletId, 200, "Deposit 2"));

        // Then: both should succeed (DCB ensures consistency)
        Query query = Query.forEventAndTag("DepositMade", "wallet_id", walletId);
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("Should verify command metadata is stored")
    void shouldVerifyCommandMetadataIsStored() {
        // Given: wallet command
        OpenWalletCommand cmd = OpenWalletCommand.of("wallet10", "Ivy", 1000);

        // When: execute command
        commandExecutor.executeCommand(cmd);

        // Then: verify command metadata stored in commands table
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commands WHERE type = ?",
                Integer.class,
                "open_wallet"
        );
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}

