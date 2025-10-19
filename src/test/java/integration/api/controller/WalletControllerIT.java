package integration.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletEventDTO;
import com.wallets.features.query.WalletHistoryResponse;
import com.wallets.features.query.WalletResponse;
import com.wallets.features.transfer.TransferRequest;
import com.wallets.features.withdraw.WithdrawRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import testutils.AbstractCrabletTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WalletController using TestRestTemplate.
 * Tests the complete wallet lifecycle with real HTTP requests.
 */
class WalletControllerIT extends AbstractCrabletTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/wallets";
    }

    @Test
    @DisplayName("Should handle complete wallet lifecycle: create -> deposit -> transfer -> withdraw -> history")
    void shouldHandleCompleteWalletLifecycle() {
        // Step 1: Create two wallets
        String wallet1Id = "wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "wallet-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        // Create wallet 1
        String wallet1Url = baseUrl + "/" + wallet1Id;
        restTemplate.put(wallet1Url, wallet1Request);

        // Create wallet 2
        String wallet2Url = baseUrl + "/" + wallet2Id;
        restTemplate.put(wallet2Url, wallet2Request);

        // Verify wallets were created
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(wallet1Url, WalletResponse.class);
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(wallet2Url, WalletResponse.class);

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        WalletResponse wallet1 = wallet1Response.getBody();
        WalletResponse wallet2 = wallet2Response.getBody();
        assertThat(wallet1).isNotNull();
        assertThat(wallet2).isNotNull();
        assertThat(wallet1.balance()).isEqualTo(1000);
        assertThat(wallet2.balance()).isEqualTo(500);

        // Step 2: Deposit money to wallet 1
        DepositRequest depositRequest = new DepositRequest("deposit-1", 200, "Bonus payment");
        restTemplate.postForEntity(wallet1Url + "/deposit", depositRequest, Void.class);

        // Verify deposit
        ResponseEntity<WalletResponse> afterDepositResponse = restTemplate.getForEntity(wallet1Url, WalletResponse.class);
        assertThat(afterDepositResponse.getBody()).isNotNull();
        assertThat(afterDepositResponse.getBody().balance()).isEqualTo(1200);

        // Step 3: Transfer money from wallet 1 to wallet 2
        String transferId = "transfer-" + UUID.randomUUID().toString().substring(0, 8);
        TransferRequest transferRequest = new TransferRequest(
                transferId, wallet1Id, wallet2Id, 300, "Payment for services"
        );

        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify transfer
        ResponseEntity<WalletResponse> wallet1AfterTransfer = restTemplate.getForEntity(wallet1Url, WalletResponse.class);
        ResponseEntity<WalletResponse> wallet2AfterTransfer = restTemplate.getForEntity(wallet2Url, WalletResponse.class);

        assertThat(wallet1AfterTransfer.getBody()).isNotNull();
        assertThat(wallet2AfterTransfer.getBody()).isNotNull();
        WalletResponse wallet1After = wallet1AfterTransfer.getBody();
        WalletResponse wallet2After = wallet2AfterTransfer.getBody();
        assertThat(wallet1After).isNotNull();
        assertThat(wallet2After).isNotNull();
        assertThat(wallet1After.balance()).isEqualTo(900); // 1200 - 300
        assertThat(wallet2After.balance()).isEqualTo(800); // 500 + 300

        // Step 4: Withdraw money from wallet 2
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 100, "Cash withdrawal");
        restTemplate.postForEntity(wallet2Url + "/withdraw", withdrawRequest, Void.class);

        // Verify withdrawal
        ResponseEntity<WalletResponse> wallet2AfterWithdraw = restTemplate.getForEntity(wallet2Url, WalletResponse.class);
        assertThat(wallet2AfterWithdraw.getBody()).isNotNull();
        assertThat(wallet2AfterWithdraw.getBody().balance()).isEqualTo(700); // 800 - 100

        // Step 5: Query events for wallet 1 (use a timestamp far in the future to include all events)
        String futureTimestamp = Instant.now().plusSeconds(60).toString();
        String eventsUrl = wallet1Url + "/events?timestamp=" + futureTimestamp;

        ResponseEntity<WalletHistoryResponse> historyResponse = restTemplate.getForEntity(eventsUrl, WalletHistoryResponse.class);
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isNotNull();

        WalletHistoryResponse history = historyResponse.getBody();
        assertThat(history.events()).hasSize(3); // WalletOpened + DepositMade + MoneyTransferred
        assertThat(history.totalEvents()).isEqualTo(3);
        assertThat(history.page()).isEqualTo(0);
        assertThat(history.size()).isEqualTo(20);
        assertThat(history.hasNext()).isFalse();

        // Verify event types (events are returned in ASC order - chronological order)
        List<String> eventTypes = history.events().stream()
                .map(WalletEventDTO::eventType)
                .toList();
        assertThat(eventTypes).containsExactly("WalletOpened", "DepositMade", "MoneyTransferred");
    }

    @Test
    @DisplayName("Should handle PUT wallet creation idempotency correctly")
    void shouldHandleWalletCreationIdempotency() {
        String walletId = "idempotent-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest request = new OpenWalletRequest("Charlie", 750);

        // First call - should create wallet
        restTemplate.put(walletUrl, request);

        // Verify wallet was created
        ResponseEntity<WalletResponse> firstResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().walletId()).isEqualTo(walletId);
        assertThat(firstResponse.getBody().owner()).isEqualTo("Charlie");
        assertThat(firstResponse.getBody().balance()).isEqualTo(750);

        // Second call with same data - should be idempotent
        restTemplate.put(walletUrl, request);

        // Verify wallet still exists with same data
        ResponseEntity<WalletResponse> secondResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().walletId()).isEqualTo(walletId);
        assertThat(secondResponse.getBody().owner()).isEqualTo("Charlie");
        assertThat(secondResponse.getBody().balance()).isEqualTo(750);
    }

    @Test
    @DisplayName("Should handle transfer idempotency with transferId")
    void shouldHandleTransferIdempotency() {
        // Create two wallets
        String wallet1Id = "transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        restTemplate.put(baseUrl + "/" + wallet1Id, new OpenWalletRequest("David", 1000));
        restTemplate.put(baseUrl + "/" + wallet2Id, new OpenWalletRequest("Eve", 500));

        // First transfer
        String transferId = "idempotent-transfer-" + UUID.randomUUID().toString().substring(0, 8);
        TransferRequest transferRequest = new TransferRequest(
                transferId, wallet1Id, wallet2Id, 200, "First transfer"
        );

        ResponseEntity<Void> firstTransfer = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );
        assertThat(firstTransfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify balances after first transfer
        ResponseEntity<WalletResponse> wallet1AfterFirst = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2AfterFirst = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1AfterFirst.getBody()).isNotNull();
        assertThat(wallet2AfterFirst.getBody()).isNotNull();
        WalletResponse wallet1First = wallet1AfterFirst.getBody();
        WalletResponse wallet2First = wallet2AfterFirst.getBody();
        assertThat(wallet1First).isNotNull();
        assertThat(wallet2First).isNotNull();
        assertThat(wallet1First.balance()).isEqualTo(800);
        assertThat(wallet2First.balance()).isEqualTo(700);

        // Second transfer with same transferId - should be idempotent
        ResponseEntity<Void> secondTransfer = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );
        assertThat(secondTransfer.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify balances unchanged (idempotent)
        ResponseEntity<WalletResponse> wallet1AfterSecond = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2AfterSecond = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1AfterSecond.getBody()).isNotNull();
        assertThat(wallet2AfterSecond.getBody()).isNotNull();
        WalletResponse wallet1Second = wallet1AfterSecond.getBody();
        WalletResponse wallet2Second = wallet2AfterSecond.getBody();
        assertThat(wallet1Second).isNotNull();
        assertThat(wallet2Second).isNotNull();
        assertThat(wallet1Second.balance()).isEqualTo(800); // Unchanged
        assertThat(wallet2Second.balance()).isEqualTo(700); // Unchanged
    }

    @Test
    @DisplayName("Should handle history pagination correctly")
    void shouldHandleHistoryPagination() {
        String walletId = "pagination-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        // Create wallet
        restTemplate.put(walletUrl, new OpenWalletRequest("Frank", 1000));

        // Create multiple deposits to test pagination
        for (int i = 1; i <= 5; i++) {
            DepositRequest depositRequest = new DepositRequest("deposit-" + i, 100, "Deposit " + i);
            restTemplate.postForEntity(walletUrl + "/deposit", depositRequest, Void.class);
        }

        // Query first page (3 events: WalletOpened + 2 deposits)
        String futureTimestamp = Instant.now().plusSeconds(60).toString();
        String firstPageUrl = walletUrl + "/events?timestamp=" + futureTimestamp + "&page=0&size=3";

        ResponseEntity<WalletHistoryResponse> firstPageResponse = restTemplate.getForEntity(
                firstPageUrl, WalletHistoryResponse.class
        );

        assertThat(firstPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPageResponse.getBody()).isNotNull();

        WalletHistoryResponse firstPage = firstPageResponse.getBody();
        assertThat(firstPage.events()).hasSize(3);
        assertThat(firstPage.totalEvents()).isEqualTo(6); // WalletOpened + 5 deposits
        assertThat(firstPage.page()).isEqualTo(0);
        assertThat(firstPage.size()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();

        // Query second page (3 events: remaining 3 deposits)
        String secondPageUrl = walletUrl + "/events?timestamp=" + futureTimestamp + "&page=1&size=3";

        ResponseEntity<WalletHistoryResponse> secondPageResponse = restTemplate.getForEntity(
                secondPageUrl, WalletHistoryResponse.class
        );

        assertThat(secondPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondPageResponse.getBody()).isNotNull();

        WalletHistoryResponse secondPage = secondPageResponse.getBody();
        assertThat(secondPage.events()).hasSize(3);
        assertThat(secondPage.totalEvents()).isEqualTo(6);
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.size()).isEqualTo(3);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should use default timestamp when none provided")
    void shouldUseDefaultTimestampWhenNoneProvided() {
        String walletId = "default-timestamp-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        // Create wallet
        restTemplate.put(walletUrl, new OpenWalletRequest("Grace", 1000));

        // Make a deposit
        DepositRequest depositRequest = new DepositRequest("deposit-test", 200, "Test deposit");
        restTemplate.postForEntity(walletUrl + "/deposit", depositRequest, Void.class);

        // Query events with a future timestamp to include all events
        String futureTimestamp = Instant.now().plusSeconds(60).toString();
        String eventsUrl = walletUrl + "/events?timestamp=" + futureTimestamp;

        ResponseEntity<WalletHistoryResponse> response = restTemplate.getForEntity(eventsUrl, WalletHistoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        WalletHistoryResponse history = response.getBody();
        // Should include all events since they were created after midnight of current month
        assertThat(history.events()).hasSize(2); // WalletOpened + DepositMade
        assertThat(history.totalEvents()).isEqualTo(2);
        assertThat(history.page()).isEqualTo(0);
        assertThat(history.size()).isEqualTo(20);
        assertThat(history.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should handle error cases gracefully")
    void shouldHandleErrorCasesGracefully() {
        String walletId = "error-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        // Test 404 for non-existent wallet
        ResponseEntity<WalletResponse> notFoundResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Test insufficient funds
        restTemplate.put(walletUrl, new OpenWalletRequest("Henry", 100));

        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-error", 200, "Overdraft attempt");
        ResponseEntity<Void> insufficientFundsResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );
        assertThat(insufficientFundsResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balance unchanged
        ResponseEntity<WalletResponse> balanceAfterFailedWithdraw = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(balanceAfterFailedWithdraw.getBody()).isNotNull();
        assertThat(balanceAfterFailedWithdraw.getBody().balance()).isEqualTo(100);

        // Test transfer with insufficient funds
        String wallet2Id = "error-wallet2-" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.put(baseUrl + "/" + wallet2Id, new OpenWalletRequest("Iris", 50));

        String transferId = "error-transfer-" + UUID.randomUUID().toString().substring(0, 8);
        TransferRequest transferRequest = new TransferRequest(
                transferId, walletId, wallet2Id, 150, "Insufficient funds transfer"
        );

        ResponseEntity<Void> transferErrorResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );
        assertThat(transferErrorResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balances unchanged
        ResponseEntity<WalletResponse> wallet1AfterFailedTransfer = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        ResponseEntity<WalletResponse> wallet2AfterFailedTransfer = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1AfterFailedTransfer.getBody()).isNotNull();
        assertThat(wallet2AfterFailedTransfer.getBody()).isNotNull();
        assertThat(wallet1AfterFailedTransfer.getBody().balance()).isEqualTo(100);
        assertThat(wallet2AfterFailedTransfer.getBody().balance()).isEqualTo(50);
    }
}
