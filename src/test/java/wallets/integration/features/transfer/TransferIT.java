package wallets.integration.features.transfer;

import com.crablet.core.CommandExecutor;
import com.crablet.core.EventStore;
import com.crablet.core.EventTestHelper;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.StoredEvent;
import com.wallets.features.openwallet.OpenWalletCommand;
import com.wallets.features.transfer.TransferMoneyCommand;
import com.wallets.features.transfer.TransferMoneyCommandHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import wallets.integration.AbstractWalletIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for transfer functionality using the Java DCB library.
 * This test mirrors the Go transfer example but uses Java/Spring Boot.
 * Tests are ordered and database is cleaned between tests for isolation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransferIT extends AbstractWalletIntegrationTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    private TransferMoneyCommandHandler commandHandler;

    @Autowired
    private com.wallets.features.openwallet.OpenWalletCommandHandler openWalletHandler;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventTestHelper testHelper;

    @BeforeEach
    void setUp() {
        // Database cleanup is handled by AbstractCrabletIT
    }

    @AfterEach
    void tearDown() {
        // Additional cleanup after each test to ensure connections are released
        try {
            // Force a small delay to allow connections to be properly closed
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should open wallet successfully")
    void testOpenWallet() {
        // Given
        OpenWalletCommand cmd = OpenWalletCommand.of("wallet1", "Alice", 1000);

        // When
        commandExecutor.executeCommand(cmd);

        // Then - verify wallet was opened
        assertThat(true).isTrue(); // Basic test passes if no exception thrown
    }

    @Test
    @Order(2)
    @DisplayName("Should open wallet and append event")
    void testOpenWalletAndAppend() {
        // Given
        OpenWalletCommand cmd = OpenWalletCommand.of("wallet1", "Alice", 1000);

        // When
        commandExecutor.executeCommand(cmd);

        // Then - verify event was created
        var events = testHelper.query(Query.empty(), null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");

        // Verify specific query
        var walletEvents = testHelper.query(Query.of(QueryItem.ofType("WalletOpened")), null);
        assertThat(walletEvents).hasSize(1);
        StoredEvent event = walletEvents.get(0);
        assertThat(event.type()).isEqualTo("WalletOpened");
        assertThat(event.hasTag("wallet_id", "wallet1")).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Should prevent duplicate wallet opening")
    void testDuplicateWalletOpening() {
        // Given
        OpenWalletCommand cmd1 = OpenWalletCommand.of("wallet1", "Alice", 1000);
        OpenWalletCommand cmd2 = OpenWalletCommand.of("wallet1", "Bob", 500);

        // When
        commandExecutor.executeCommand(cmd1);

        // Then - verify duplicate wallet opening throws exception
        assertThatThrownBy(() -> commandExecutor.executeCommand(cmd2))
                .isInstanceOf(com.crablet.core.ConcurrencyException.class);

        // Verify only one event exists (duplicate prevented)
        var events = testHelper.query(Query.empty(), null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
    }

    @Test
    @Order(4)
    @DisplayName("Should transfer money between wallets")
    void testTransferMoney() {
        // Given - First create two wallets
        OpenWalletCommand openWallet1 = OpenWalletCommand.of("wallet1", "Alice", 1000);
        OpenWalletCommand openWallet2 = OpenWalletCommand.of("wallet2", "Bob", 500);
        TransferMoneyCommand transferCmd = TransferMoneyCommand.of(
                "tx1", "wallet1", "wallet2", 300, "Test transfer"
        );

        // When - Execute all commands sequentially
        commandExecutor.executeCommand(openWallet1);
        commandExecutor.executeCommand(openWallet2);
        commandExecutor.executeCommand(transferCmd);

        // Then - Query all events after transfer
        var events = testHelper.query(Query.empty(), null);

        // Verify transfer event was created
        long transferEvents = events.stream()
                .filter(e -> "MoneyTransferred".equals(e.type()))
                .count();
        assertThat(transferEvents).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("Should transfer money and append event")
    void testTransferMoneyAndAppend() {
        // Given
        OpenWalletCommand openWallet1 = OpenWalletCommand.of("wallet1", "Alice", 1000);
        OpenWalletCommand openWallet2 = OpenWalletCommand.of("wallet2", "Bob", 500);
        TransferMoneyCommand transferCmd = TransferMoneyCommand.of(
                "tx1", "wallet1", "wallet2", 300, "Test transfer"
        );

        // When - Open wallets and transfer
        commandExecutor.executeCommand(openWallet1);
        commandExecutor.executeCommand(openWallet2);
        commandExecutor.executeCommand(transferCmd);

        // Then - Verify transfer event was stored
        Query query = Query.of(QueryItem.ofType("MoneyTransferred"));
        var events = testHelper.query(query, null);

        assertThat(events).hasSize(1);
        StoredEvent event = events.get(0);
        assertThat(event.type()).isEqualTo("MoneyTransferred");
        assertThat(event.hasTag("transfer_id", "tx1")).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Should prevent transfer with insufficient funds")
    void testInsufficientFunds() {
        // Given
        OpenWalletCommand openWallet1 = OpenWalletCommand.of("wallet1", "Alice", 100);
        OpenWalletCommand openWallet2 = OpenWalletCommand.of("wallet2", "Bob", 500);
        TransferMoneyCommand transferCmd = TransferMoneyCommand.of(
                "tx1", "wallet1", "wallet2", 200, "Transfer more than available"
        );

        // When - Open wallets
        commandExecutor.executeCommand(openWallet1);
        commandExecutor.executeCommand(openWallet2);

        // Then - Attempt transfer with insufficient funds should throw exception
        assertThatThrownBy(() -> commandExecutor.executeCommand(transferCmd))
                .isInstanceOf(com.wallets.domain.exception.InsufficientFundsException.class);
    }

    @Test
    @Order(7)
    @DisplayName("Should prevent transfer to non-existent wallet")
    void testTransferToNonExistentWallet() {
        // Given
        OpenWalletCommand openWallet1 = OpenWalletCommand.of("wallet1", "Alice", 1000);
        TransferMoneyCommand transferCmd = TransferMoneyCommand.of(
                "tx1", "wallet1", "nonexistent", 300, "Transfer to non-existent wallet"
        );

        // When - Open only one wallet
        commandExecutor.executeCommand(openWallet1);

        // Then - Attempt transfer to non-existent wallet should throw exception
        assertThatThrownBy(() -> commandExecutor.executeCommand(transferCmd))
                .isInstanceOf(com.wallets.domain.exception.WalletNotFoundException.class);
    }

    @Test
    @Order(8)
    @DisplayName("Should handle complete transfer scenario")
    void testCompleteTransferScenario() {
        // Given - Complete scenario from Go example
        OpenWalletCommand openWallet1 = OpenWalletCommand.of("wallet1", "Alice", 1000);
        OpenWalletCommand openWallet2 = OpenWalletCommand.of("wallet456", "Bob", 500);
        TransferMoneyCommand transfer1 = TransferMoneyCommand.of(
                "tx1", "wallet1", "wallet456", 300, "First transfer"
        );
        TransferMoneyCommand transfer2 = TransferMoneyCommand.of(
                "tx2", "wallet456", "wallet1", 100, "Return transfer"
        );

        // When - Execute all commands sequentially
        commandExecutor.executeCommand(openWallet1);
        commandExecutor.executeCommand(openWallet2);
        commandExecutor.executeCommand(transfer1);
        commandExecutor.executeCommand(transfer2);

        // Then - Verify all events were stored
        Query allEventsQuery = Query.empty();
        var events = testHelper.query(allEventsQuery, null);

        assertThat(events).hasSize(4); // 2 WalletOpened + 2 MoneyTransferred

        long walletOpenedCount = events.stream()
                .filter(e -> "WalletOpened".equals(e.type()))
                .count();
        long moneyTransferredCount = events.stream()
                .filter(e -> "MoneyTransferred".equals(e.type()))
                .count();

        assertThat(walletOpenedCount).isEqualTo(2);
        assertThat(moneyTransferredCount).isEqualTo(2);
    }
}
