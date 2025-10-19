package integration.api.query;

import com.crablet.core.CommandExecutor;
import com.crablet.core.EventStore;
import com.wallets.domain.event.DepositMade;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletEvent;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.features.deposit.DepositCommand;
import com.wallets.features.openwallet.OpenWalletCommand;
import com.wallets.features.query.WalletCommandsResponse;
import com.wallets.features.query.WalletCommandDTO;
import com.wallets.features.query.WalletEventDTO;
import com.wallets.features.query.WalletHistoryResponse;
import com.wallets.features.query.WalletQueryRepository;
import com.wallets.features.query.WalletQueryService;
import com.wallets.features.query.WalletResponse;
import com.wallets.features.transfer.TransferMoneyCommand;
import com.wallets.features.withdraw.WithdrawCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;
import testutils.WalletTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WalletQueryService using real database.
 * Tests the complete query stack: service → repository → PostgreSQL.
 * 
 * This test validates:
 * - Wallet state projection from events
 * - Event pagination and filtering
 * - Command history with nested events
 * - SQL query correctness
 */
@DisplayName("WalletQueryService Integration Tests")
class WalletQueryServiceIT extends AbstractCrabletTest {

    @Autowired
    private WalletQueryService queryService;
    
    @Autowired
    private WalletQueryRepository queryRepository;
    
    @Autowired
    private CommandExecutor commandExecutor;
    
    private String walletId1;
    private String walletId2;

    @BeforeEach
    void setUp() {
        walletId1 = WalletTestUtils.createWalletId("wallet1");
        walletId2 = WalletTestUtils.createWalletId("wallet2");
    }

    @Test
    @DisplayName("Should get wallet state with multiple events")
    void shouldGetWalletStateWithMultipleEvents() {
        // Given: wallet with opened → deposit → withdrawal sequence
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "Bonus"));
        executeCommand(new WithdrawCommand("withdraw-1", walletId1, 200, "Cash"));

        // When: get wallet state
        WalletResponse response = queryService.getWalletState(walletId1);

        // Then: returns correct final balance and owner
        assertThat(response).isNotNull();
        assertThat(response.walletId()).isEqualTo(walletId1);
        assertThat(response.owner()).isEqualTo("Alice");
        assertThat(response.balance()).isEqualTo(1300); // 1000 + 500 - 200
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return null for nonexistent wallet")
    void shouldReturnNullForNonexistentWallet() {
        // Given: wallet does not exist
        String nonexistentWalletId = "nonexistent-" + UUID.randomUUID();

        // When: get wallet state
        WalletResponse response = queryService.getWalletState(nonexistentWalletId);

        // Then: returns null
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("Should handle transfer events correctly")
    void shouldHandleTransferEventsCorrectly() {
        // Given: two wallets and a transfer between them
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new OpenWalletCommand(walletId2, "Bob", 500));
        executeCommand(new TransferMoneyCommand("transfer-1", walletId1, walletId2, 300, "Payment"));

        // When: get wallet state for both wallets
        WalletResponse wallet1Response = queryService.getWalletState(walletId1);
        WalletResponse wallet2Response = queryService.getWalletState(walletId2);

        // Then: both have correct balances
        assertThat(wallet1Response).isNotNull();
        assertThat(wallet1Response.balance()).isEqualTo(700); // 1000 - 300

        assertThat(wallet2Response).isNotNull();
        assertThat(wallet2Response.balance()).isEqualTo(800); // 500 + 300
    }

    @Test
    @DisplayName("Should paginate wallet events")
    void shouldPaginateWalletEvents() {
        // Given: wallet with 10 events
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        for (int i = 1; i <= 9; i++) {
            executeCommand(new DepositCommand("deposit-" + i, walletId1, 100, "Deposit " + i));
        }

        // When: get first page
        WalletHistoryResponse page1 = queryService.getWalletHistory(walletId1, null, 0, 3);

        // Then: returns 3 events, hasNext=true, totalEvents=10
        assertThat(page1.events()).hasSize(3);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalEvents()).isEqualTo(10);
        assertThat(page1.page()).isEqualTo(0);
        assertThat(page1.size()).isEqualTo(3);

        // When: get second page
        WalletHistoryResponse page2 = queryService.getWalletHistory(walletId1, null, 1, 3);

        // Then: returns 3 events, hasNext=true
        assertThat(page2.events()).hasSize(3);
        assertThat(page2.hasNext()).isTrue();
        assertThat(page2.page()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should detect last page")
    void shouldDetectLastPage() {
        // Given: wallet with 5 events
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        for (int i = 1; i <= 4; i++) {
            executeCommand(new DepositCommand("deposit-" + i, walletId1, 100, "Deposit " + i));
        }

        // When: get page 1 with size 3
        WalletHistoryResponse response = queryService.getWalletHistory(walletId1, null, 1, 3);

        // Then: returns 2 events, hasNext=false
        assertThat(response.events()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalEvents()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should filter events by timestamp")
    void shouldFilterEventsByTimestamp() {
        // Given: events at different timestamps
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "First deposit"));
        
        Instant filterTime = Instant.now();
        
        executeCommand(new DepositCommand("deposit-2", walletId1, 300, "Second deposit"));

        // When: get history with timestamp filter
        WalletHistoryResponse response = queryService.getWalletHistory(walletId1, filterTime, 0, 10);

        // Then: only returns events before timestamp
        assertThat(response.events()).hasSize(2); // OpenWallet + first deposit
        assertThat(response.totalEvents()).isEqualTo(2);
        
        // Verify events are in correct order (newest first)
        List<WalletEventDTO> events = response.events();
        assertThat(events.get(0).eventType()).isEqualTo("WalletOpened");
        assertThat(events.get(1).eventType()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should handle empty wallet history")
    void shouldHandleEmptyWalletHistory() {
        // Given: wallet with no events (just opened)
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));

        // When: get history
        WalletHistoryResponse response = queryService.getWalletHistory(walletId1, null, 0, 10);

        // Then: returns empty list, hasNext=false
        assertThat(response.events()).hasSize(1); // Only the WalletOpened event
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should get commands with nested events")
    void shouldGetCommandsWithNestedEvents() {
        // Given: multiple commands
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new OpenWalletCommand(walletId2, "Bob", 500));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "Bonus"));
        executeCommand(new TransferMoneyCommand("transfer-1", walletId1, walletId2, 300, "Payment"));

        // When: get commands
        WalletCommandsResponse response = queryService.getWalletCommands(walletId1, null, 0, 10);

        // Then: returns commands with nested events
        assertThat(response.commands()).hasSize(3);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalCommands()).isEqualTo(3);

        // Verify first command (transfer) has nested events
        WalletCommandDTO transferCommand = response.commands().get(0); // Most recent first
        assertThat(transferCommand.commandType()).isEqualTo("transfer_money");
        assertThat(transferCommand.events()).hasSize(1); // MoneyTransferred event
    }

    @Test
    @DisplayName("Should handle multi-wallet tags")
    void shouldHandleMultiWalletTags() {
        // Given: transfer command (affects 2 wallets)
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new OpenWalletCommand(walletId2, "Bob", 500));
        executeCommand(new TransferMoneyCommand("transfer-1", walletId1, walletId2, 300, "Payment"));

        // When: get commands for both wallets
        WalletCommandsResponse wallet1Commands = queryService.getWalletCommands(walletId1, null, 0, 10);
        WalletCommandsResponse wallet2Commands = queryService.getWalletCommands(walletId2, null, 0, 10);

        // Then: both see the transfer command
        assertThat(wallet1Commands.commands()).hasSize(2); // OpenWallet + Transfer
        assertThat(wallet2Commands.commands()).hasSize(2); // OpenWallet + Transfer

        // Verify transfer command appears in both
        assertThat(wallet1Commands.commands().get(0).commandType()).isEqualTo("transfer_money");
        assertThat(wallet2Commands.commands().get(0).commandType()).isEqualTo("transfer_money");
    }

    @Test
    @DisplayName("Should paginate commands")
    void shouldPaginateCommands() {
        // Given: 10 commands
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        for (int i = 1; i <= 9; i++) {
            executeCommand(new DepositCommand("deposit-" + i, walletId1, 100, "Deposit " + i));
        }

        // When: get first page
        WalletCommandsResponse page1 = queryService.getWalletCommands(walletId1, null, 0, 5);

        // Then: returns 5 commands, hasNext=true
        assertThat(page1.commands()).hasSize(5);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.totalCommands()).isEqualTo(10);
        assertThat(page1.page()).isEqualTo(0);
        assertThat(page1.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should filter commands by timestamp")
    void shouldFilterCommandsByTimestamp() {
        // Given: commands at different times
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "First deposit"));
        
        Instant filterTime = Instant.now();
        
        executeCommand(new DepositCommand("deposit-2", walletId1, 300, "Second deposit"));

        // When: get commands with timestamp filter
        WalletCommandsResponse response = queryService.getWalletCommands(walletId1, filterTime, 0, 10);

        // Then: only returns filtered commands
        assertThat(response.commands()).hasSize(2); // OpenWallet + first deposit
        assertThat(response.totalCommands()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should aggregate events as JSON array")
    void shouldAggregateEventsAsJsonArray() {
        // Given: wallet with events
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "Bonus"));

        // When: repository gets events as JSON array
        byte[] jsonArray = queryRepository.getWalletEventsAsJsonArray(walletId1);

        // Then: returns valid JSON array, can be deserialized
        assertThat(jsonArray).isNotNull();
        assertThat(jsonArray.length).isGreaterThan(0);
        
        String jsonString = new String(jsonArray);
        assertThat(jsonString).startsWith("[");
        assertThat(jsonString).endsWith("]");
        assertThat(jsonString).contains("WalletOpened");
        assertThat(jsonString).contains("DepositMade");
    }

    @Test
    @DisplayName("Should handle empty JSON array")
    void shouldHandleEmptyJsonArray() {
        // Given: no events for wallet
        String emptyWalletId = "empty-" + UUID.randomUUID();

        // When: get events as JSON array
        byte[] jsonArray = queryRepository.getWalletEventsAsJsonArray(emptyWalletId);

        // Then: returns "[]"
        String jsonString = new String(jsonArray);
        assertThat(jsonString).isEqualTo("[]");
    }

    @Test
    @DisplayName("Should order events by transaction and position")
    void shouldOrderEventsByTransactionAndPosition() {
        // Given: events in mixed order (multiple commands)
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "First"));
        executeCommand(new DepositCommand("deposit-2", walletId1, 300, "Second"));

        // When: get events
        List<com.wallets.features.query.EventResponse> events = queryRepository.getWalletEvents(walletId1, null, 10, 0);

        // Then: returned in correct order (transaction_id, position ASC)
        assertThat(events).hasSize(3);
        
        // Verify ordering - should be chronological
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
        assertThat(events.get(1).type()).isEqualTo("DepositMade");
        assertThat(events.get(2).type()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should handle complex event sequences")
    void shouldHandleComplexEventSequences() {
        // Given: complex sequence: open → deposit → transfer → withdraw
        executeCommand(new OpenWalletCommand(walletId1, "Alice", 1000));
        executeCommand(new OpenWalletCommand(walletId2, "Bob", 500));
        executeCommand(new DepositCommand("deposit-1", walletId1, 500, "Bonus"));
        executeCommand(new TransferMoneyCommand("transfer-1", walletId1, walletId2, 300, "Payment"));
        executeCommand(new WithdrawCommand("withdraw-1", walletId1, 200, "Cash"));

        // When: get final state
        WalletResponse wallet1State = queryService.getWalletState(walletId1);
        WalletResponse wallet2State = queryService.getWalletState(walletId2);

        // Then: both wallets have correct final balances
        assertThat(wallet1State.balance()).isEqualTo(1000); // 1000 + 500 - 300 - 200
        assertThat(wallet2State.balance()).isEqualTo(800);  // 500 + 300

        // When: get history for wallet1
        WalletHistoryResponse history = queryService.getWalletHistory(walletId1, null, 0, 10);

        // Then: all events are present in correct order
        assertThat(history.events()).hasSize(4); // OpenWallet, DepositMade, MoneyTransferred, WithdrawalMade
        assertThat(history.totalEvents()).isEqualTo(4);
    }

    /**
     * Helper method to execute commands and wait for completion.
     */
    private void executeCommand(com.crablet.core.Command command) {
        try {
            commandExecutor.executeCommand(command);
            // Small delay to ensure events are persisted
            Thread.sleep(10);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: " + command, e);
        }
    }
}
