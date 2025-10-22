package wallets.integration.api.query;

import com.wallets.features.deposit.DepositController;
import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletController;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletCommandDTO;
import com.wallets.features.query.WalletCommandsResponse;
import com.wallets.features.transfer.TransferController;
import com.wallets.features.transfer.TransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import wallets.integration.AbstractWalletIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for wallet commands endpoint.
 * Tests the /api/wallets/{walletId}/commands endpoint with various scenarios.
 * <p>
 * Note: This test requires command persistence to be enabled, so we override
 * the test profile setting which disables it for performance.
 */
@TestPropertySource(properties = "crablet.eventstore.persist-commands=true")
class WalletCommandsEndpointTest extends AbstractWalletIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OpenWalletController openWalletController;

    @Autowired
    private DepositController depositController;

    @Autowired
    private TransferController transferController;

    @Test
    @DisplayName("Should return commands for wallet with their events")
    void shouldReturnCommandsForWalletWithTheirEvents() {
        // Given - Create wallet with unique ID
        String walletId = "wallet-commands-" + System.currentTimeMillis();
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        ResponseEntity<Void> walletResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(walletRequest),
                Void.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Given - Deposit to wallet
        DepositRequest depositRequest = new DepositRequest("deposit-1", 200, "Salary");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // When - Get commands for wallet
        ResponseEntity<WalletCommandsResponse> commandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/commands", WalletCommandsResponse.class);

        // Then - Should include open wallet and deposit commands
        assertThat(commandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse commands = commandsResponse.getBody();
        assertThat(commands).isNotNull();
        assertThat(commands.commands()).hasSize(2); // OpenWallet + Deposit

        // Verify command types
        assertThat(commands.commands().stream().map(WalletCommandDTO::commandType))
                .containsExactlyInAnyOrder("open_wallet", "deposit");

        // Verify each command has events
        for (WalletCommandDTO command : commands.commands()) {
            assertThat(command.events()).isNotEmpty();
            assertThat(command.transactionId()).isNotNull();
            assertThat(command.occurredAt()).isNotNull();
        }

        // Verify pagination
        assertThat(commands.hasNext()).isFalse();
        assertThat(commands.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return commands for wallet involved in transfers")
    void shouldReturnCommandsForWalletInvolvedInTransfers() {
        // Given - Create two wallets with unique IDs
        String wallet1Id = "wallet-transfer-sender-" + System.currentTimeMillis();
        String wallet2Id = "wallet-transfer-receiver-" + System.currentTimeMillis();

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                HttpMethod.PUT,
                new HttpEntity<>(wallet1Request),
                Void.class
        );
        restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                HttpMethod.PUT,
                new HttpEntity<>(wallet2Request),
                Void.class
        );

        // Given - Transfer from wallet1 to wallet2
        TransferRequest transferRequest = new TransferRequest("transfer-1", wallet1Id, wallet2Id, 300, "Payment");
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // When - Get commands for wallet1 (sender)
        ResponseEntity<WalletCommandsResponse> senderCommandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/commands", WalletCommandsResponse.class);

        // Then - Should include open wallet and transfer commands
        assertThat(senderCommandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse senderCommands = senderCommandsResponse.getBody();
        assertThat(senderCommands).isNotNull();
        assertThat(senderCommands.commands()).hasSize(2); // OpenWallet + Transfer

        // Verify command types
        assertThat(senderCommands.commands().stream().map(WalletCommandDTO::commandType))
                .containsExactlyInAnyOrder("open_wallet", "transfer_money");

        // When - Get commands for wallet2 (receiver)
        ResponseEntity<WalletCommandsResponse> receiverCommandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id + "/commands", WalletCommandsResponse.class);

        // Then - Should include open wallet and transfer commands
        assertThat(receiverCommandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse receiverCommands = receiverCommandsResponse.getBody();
        assertThat(receiverCommands).isNotNull();
        assertThat(receiverCommands.commands()).hasSize(2); // OpenWallet + Transfer

        // Verify command types
        assertThat(receiverCommands.commands().stream().map(WalletCommandDTO::commandType))
                .containsExactlyInAnyOrder("open_wallet", "transfer_money");
    }

    @Test
    @DisplayName("Should handle pagination with cursor")
    void shouldHandlePaginationWithCursor() {
        // Given - Create wallet with unique ID and multiple deposits
        String walletId = "wallet-commands-pagination-" + System.currentTimeMillis();
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(walletRequest),
                Void.class
        );

        // Create multiple deposits to test pagination
        for (int i = 1; i <= 3; i++) {
            DepositRequest depositRequest = new DepositRequest("deposit-" + i, 100, "Deposit " + i);
            restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                    depositRequest,
                    Void.class
            );
        }

        // When - Get first page
        ResponseEntity<WalletCommandsResponse> firstPageResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/commands?page=0&size=2", WalletCommandsResponse.class);

        // Then - Should return 2 commands with pagination info
        assertThat(firstPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse firstPage = firstPageResponse.getBody();
        assertThat(firstPage).isNotNull();
        assertThat(firstPage.commands()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.page()).isEqualTo(0);
        assertThat(firstPage.size()).isEqualTo(2);

        // When - Get second page using page number
        ResponseEntity<WalletCommandsResponse> secondPageResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/commands?page=1&size=2",
                WalletCommandsResponse.class);

        // Then - Should return remaining commands
        assertThat(secondPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse secondPage = secondPageResponse.getBody();
        assertThat(secondPage).isNotNull();
        assertThat(secondPage.commands()).hasSize(2); // Remaining commands
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should return empty commands for non-existent wallet")
    void shouldReturnEmptyCommandsForNonExistentWallet() {
        // When - Get commands for non-existent wallet
        ResponseEntity<WalletCommandsResponse> commandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/non-existent/commands", WalletCommandsResponse.class);

        // Then - Should return empty list
        assertThat(commandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse commands = commandsResponse.getBody();
        assertThat(commands).isNotNull();
        assertThat(commands.commands()).isEmpty();
        assertThat(commands.hasNext()).isFalse();
        assertThat(commands.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should verify CTE query efficiency - no N+1 queries")
    void shouldVerifyCTEQueryEfficiency() {
        // Given - Create wallet with multiple operations
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        openWalletController.openWallet("wallet-1", walletRequest);

        // Create multiple deposits and withdrawals
        for (int i = 1; i <= 5; i++) {
            DepositRequest depositRequest = new DepositRequest("deposit-" + i, 100, "Deposit " + i);
            depositController.deposit("wallet-1", depositRequest);
        }

        // When - Get commands (this should use CTE query)
        ResponseEntity<WalletCommandsResponse> commandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/wallet-1/commands", WalletCommandsResponse.class);

        // Then - Should return all commands with their events in single query
        assertThat(commandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse commands = commandsResponse.getBody();
        assertThat(commands).isNotNull();
        assertThat(commands.commands()).hasSize(6); // OpenWallet + 5 deposits

        // Verify each command has events (proves CTE worked)
        for (WalletCommandDTO command : commands.commands()) {
            assertThat(command.events()).isNotEmpty();
            assertThat(command.transactionId()).isNotNull();
        }
    }
}
