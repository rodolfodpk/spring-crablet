package wallets.integration.api;

import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletCommandsResponse;
import com.wallets.features.query.WalletHistoryResponse;
import com.wallets.features.transfer.TransferRequest;
import com.wallets.features.withdraw.WithdrawRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import wallets.integration.AbstractWalletIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete wallet API workflow.
 * Tests the happy path: open wallets, deposit, withdraw, transfer, and query operations.
 * <p>
 * Note: This test requires command persistence to be enabled.
 */
@DisplayName("Wallet API Integration Tests")
@TestPropertySource(properties = "crablet.eventstore.persist-commands=true")
class WalletApiIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Complete wallet workflow: open wallets, deposit, withdraw, transfer, query events and commands")
    void shouldCompleteWalletWorkflow() throws Exception {
        String walletId1 = "workflow-" + UUID.randomUUID().toString().substring(0, 8);
        String walletId2 = "workflow-" + UUID.randomUUID().toString().substring(0, 8);

        // Step 1: Open wallet 1
        OpenWalletRequest openWallet1Request = new OpenWalletRequest("user1", 1000);
        ResponseEntity<Void> wallet1Response = openWallet(baseUrl + "/api/wallets/" + walletId1, openWallet1Request);
        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(wallet1Response.getHeaders().getLocation()).isNotNull();

        // Step 2: Open wallet 2
        OpenWalletRequest openWallet2Request = new OpenWalletRequest("user2", 500);
        ResponseEntity<Void> wallet2Response = openWallet(baseUrl + "/api/wallets/" + walletId2, openWallet2Request);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(wallet2Response.getHeaders().getLocation()).isNotNull();

        // Step 3: Deposit to wallet 1
        DepositRequest depositRequest = new DepositRequest("deposit-" + UUID.randomUUID().toString().substring(0, 8), 200, "Initial deposit");
        ResponseEntity<Void> depositResponse = deposit(baseUrl + "/api/wallets/" + walletId1 + "/deposit", depositRequest);
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 4: Withdraw from wallet 1
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-" + UUID.randomUUID().toString().substring(0, 8), 100, "Cash withdrawal");
        ResponseEntity<Void> withdrawResponse = withdraw(baseUrl + "/api/wallets/" + walletId1 + "/withdraw", withdrawRequest);
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 5: Transfer from wallet 1 to wallet 2
        TransferRequest transferRequest = new TransferRequest("transfer-" + UUID.randomUUID().toString().substring(0, 8), walletId1, walletId2, 150, "Transfer to wallet 2");
        ResponseEntity<Void> transferResponse = transfer(baseUrl + "/api/wallets/transfer", transferRequest);
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 6: Query events for wallet 1
        ResponseEntity<WalletHistoryResponse> eventsResponse = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId1 + "/events?page=0&size=20", WalletHistoryResponse.class);
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletHistoryResponse events = eventsResponse.getBody();
        assertThat(events).isNotNull();
        assertThat(events.events()).hasSize(4); // WalletOpened, DepositMade, MoneyWithdrawn, MoneyTransferred
        assertThat(events.events().stream().map(e -> e.eventType()))
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred");
        assertThat(events.totalEvents()).isEqualTo(4);
        assertThat(events.hasNext()).isFalse();

        // Step 7: Query events for wallet 2
        ResponseEntity<WalletHistoryResponse> events2Response = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId2 + "/events?page=0&size=20", WalletHistoryResponse.class);
        assertThat(events2Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletHistoryResponse events2 = events2Response.getBody();
        assertThat(events2).isNotNull();
        assertThat(events2.events()).hasSize(2); // WalletOpened, MoneyTransferred
        assertThat(events2.events().stream().map(e -> e.eventType()))
                .containsExactlyInAnyOrder("WalletOpened", "MoneyTransferred");
        assertThat(events2.totalEvents()).isEqualTo(2);

        // Step 8: Query commands for wallet 1
        ResponseEntity<WalletCommandsResponse> commandsResponse = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId1 + "/commands?page=0&size=20", WalletCommandsResponse.class);
        assertThat(commandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletCommandsResponse commands = commandsResponse.getBody();
        assertThat(commands).isNotNull();
        assertThat(commands.commands()).hasSize(4); // open_wallet, deposit, withdraw, transfer_money
        assertThat(commands.commands().stream().map(c -> c.commandType()))
                .containsExactlyInAnyOrder("open_wallet", "deposit", "withdraw", "transfer_money");
        assertThat(commands.totalCommands()).isEqualTo(4);
        assertThat(commands.hasNext()).isFalse();

        // Step 9: Query commands for wallet 2
        ResponseEntity<WalletCommandsResponse> commands2Response = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId2 + "/commands?page=0&size=20", WalletCommandsResponse.class);
        assertThat(commands2Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletCommandsResponse commands2 = commands2Response.getBody();
        assertThat(commands2).isNotNull();
        assertThat(commands2.commands()).hasSize(2); // open_wallet, transfer_money
        assertThat(commands2.commands().stream().map(c -> c.commandType()))
                .containsExactlyInAnyOrder("open_wallet", "transfer_money");
        assertThat(commands2.totalCommands()).isEqualTo(2);

        // Step 10: Verify wallet state
        ResponseEntity<String> wallet1StateResponse = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId1, String.class);
        assertThat(wallet1StateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> wallet2StateResponse = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId2, String.class);
        assertThat(wallet2StateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Error scenario: insufficient funds for withdrawal")
    void shouldHandleInsufficientFundsForWithdrawal() throws Exception {
        String walletId = "insufficient-" + UUID.randomUUID().toString().substring(0, 8);

        // Open wallet with small balance
        OpenWalletRequest openRequest = new OpenWalletRequest("user1", 50);
        ResponseEntity<Void> walletResponse = openWallet(baseUrl + "/api/wallets/" + walletId, openRequest);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to withdraw more than available
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 100, "Large withdrawal");
        ResponseEntity<String> withdrawResponse = withdrawWithError(baseUrl + "/api/wallets/" + walletId + "/withdraw", withdrawRequest);
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String errorBody = withdrawResponse.getBody();
        assertThat(errorBody).contains("Insufficient funds");
    }

    @Test
    @DisplayName("Error scenario: insufficient funds for transfer")
    void shouldHandleInsufficientFundsForTransfer() throws Exception {
        String walletId1 = "transfer-" + UUID.randomUUID().toString().substring(0, 8);
        String walletId2 = "transfer-" + UUID.randomUUID().toString().substring(0, 8);

        // Open wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("user1", 50);
        ResponseEntity<Void> wallet1Response = openWallet(baseUrl + "/api/wallets/" + walletId1, openRequest1);
        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        OpenWalletRequest openRequest2 = new OpenWalletRequest("user2", 0);
        ResponseEntity<Void> wallet2Response = openWallet(baseUrl + "/api/wallets/" + walletId2, openRequest2);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to transfer more than available
        TransferRequest transferRequest = new TransferRequest("transfer-" + UUID.randomUUID().toString().substring(0, 8), walletId1, walletId2, 100, "Large transfer");
        ResponseEntity<String> transferResponse = transferWithError(baseUrl + "/api/wallets/transfer", transferRequest);
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String errorBody = transferResponse.getBody();
        assertThat(errorBody).contains("Insufficient funds");
    }

    @Test
    @DisplayName("Error scenario: duplicate wallet creation")
    void shouldHandleDuplicateWalletCreation() throws Exception {
        String walletId = "duplicate-" + UUID.randomUUID().toString().substring(0, 8);

        // Open wallet first time
        OpenWalletRequest openRequest = new OpenWalletRequest("user1", 1000);
        ResponseEntity<Void> firstResponse = openWallet(baseUrl + "/api/wallets/" + walletId, openRequest);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to open same wallet again
        ResponseEntity<String> secondResponse = openWalletWithError(baseUrl + "/api/wallets/" + walletId, openRequest);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK); // Idempotent operation

        String responseBody = secondResponse.getBody();
        assertThat(responseBody).contains("Wallet already exists");
    }

    @Test
    @DisplayName("Error scenario: wallet not found for operations")
    void shouldHandleWalletNotFound() throws Exception {
        String walletId = "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);

        // Try to deposit to non-existent wallet
        DepositRequest depositRequest = new DepositRequest("deposit-" + UUID.randomUUID().toString().substring(0, 8), 100, "Deposit to non-existent wallet");
        ResponseEntity<String> depositResponse = depositWithError(baseUrl + "/api/wallets/" + walletId + "/deposit", depositRequest);
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String errorBody = depositResponse.getBody();
        assertThat(errorBody).contains("Wallet not found");
    }

    @Test
    @DisplayName("Timestamp filtering for events and commands")
    void shouldFilterEventsAndCommandsByTimestamp() throws Exception {
        String walletId = "timestamp-" + UUID.randomUUID().toString().substring(0, 8);

        // Open wallet and perform operations
        OpenWalletRequest openRequest = new OpenWalletRequest("user1", 1000);
        ResponseEntity<Void> walletResponse = openWallet(baseUrl + "/api/wallets/" + walletId, openRequest);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        DepositRequest depositRequest = new DepositRequest("deposit-" + UUID.randomUUID().toString().substring(0, 8), 200, "Deposit");
        ResponseEntity<Void> depositResponse = deposit(baseUrl + "/api/wallets/" + walletId + "/deposit", depositRequest);
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Get timestamp from 1 hour in the future for filtering (should return all events)
        // Note: We need to use a timestamp that includes our events (after they occurred)
        String futureTimestamp = java.time.Instant.now().plus(java.time.Duration.ofHours(1)).toString();

        // Query events with timestamp filter (should return all events since we're filtering up to 1 hour in the future)
        ResponseEntity<WalletHistoryResponse> eventsResponse = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId + "/events?timestamp=" + futureTimestamp + "&page=0&size=20",
                WalletHistoryResponse.class);
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletHistoryResponse events = eventsResponse.getBody();
        assertThat(events).isNotNull();
        assertThat(events.events()).hasSize(2); // WalletOpened, DepositMade

        // Query commands with timestamp filter
        ResponseEntity<WalletCommandsResponse> commandsResponse = restTemplate.getForEntity(
                baseUrl + "/api/wallets/" + walletId + "/commands?timestamp=" + futureTimestamp + "&page=0&size=20",
                WalletCommandsResponse.class);
        assertThat(commandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletCommandsResponse commands = commandsResponse.getBody();
        assertThat(commands).isNotNull();
        assertThat(commands.commands()).hasSize(2); // open_wallet, deposit
    }

    // Helper methods for API calls

    private ResponseEntity<Void> openWallet(String url, OpenWalletRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OpenWalletRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
    }

    private ResponseEntity<String> openWalletWithError(String url, OpenWalletRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OpenWalletRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
    }

    private ResponseEntity<Void> deposit(String url, DepositRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DepositRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
    }

    private ResponseEntity<String> depositWithError(String url, DepositRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DepositRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    private ResponseEntity<Void> withdraw(String url, WithdrawRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WithdrawRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
    }

    private ResponseEntity<String> withdrawWithError(String url, WithdrawRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WithdrawRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    private ResponseEntity<Void> transfer(String url, TransferRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
    }

    private ResponseEntity<String> transferWithError(String url, TransferRequest request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }
}
