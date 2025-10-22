package wallets.integration.api.query;

import com.wallets.features.deposit.DepositController;
import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletController;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletEventDTO;
import com.wallets.features.query.WalletHistoryResponse;
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
import wallets.integration.AbstractWalletIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for wallet events endpoint.
 * Tests the /api/wallets/{walletId}/events endpoint with various scenarios.
 */
class WalletEventsEndpointTest extends AbstractWalletIntegrationTest {

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
    @DisplayName("Should return events for wallet including transfers")
    void shouldReturnEventsForWalletIncludingTransfers() {
        // Given - Create two wallets with unique IDs
        String wallet1Id = "wallet-events-" + System.currentTimeMillis() + "-1";
        String wallet2Id = "wallet-events-" + System.currentTimeMillis() + "-2";

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        ResponseEntity<Void> wallet1Response = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                HttpMethod.PUT,
                new HttpEntity<>(wallet1Request),
                Void.class
        );
        ResponseEntity<Void> wallet2Response = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                HttpMethod.PUT,
                new HttpEntity<>(wallet2Request),
                Void.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Given - Deposit to wallet1
        DepositRequest depositRequest = new DepositRequest("deposit-1", 200, "Salary");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Given - Transfer from wallet1 to wallet2
        TransferRequest transferRequest = new TransferRequest("transfer-1", wallet1Id, wallet2Id, 300, "Payment");
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // When - Get events for wallet1
        ResponseEntity<WalletHistoryResponse> eventsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/events?page=0&size=20", WalletHistoryResponse.class);

        // Then - Should include wallet opened, deposit, and transfer events
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse events = eventsResponse.getBody();
        assertThat(events).isNotNull();
        assertThat(events.events()).hasSize(3); // WalletOpened + DepositMade + MoneyTransferred (as sender)

        // Verify event types
        assertThat(events.events().stream().map(WalletEventDTO::eventType))
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "MoneyTransferred");

        // Verify pagination
        assertThat(events.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should return events for wallet as transfer receiver")
    void shouldReturnEventsForWalletAsTransferReceiver() {
        // Given - Create two wallets with unique IDs
        String wallet1Id = "wallet-receiver-" + System.currentTimeMillis() + "-1";
        String wallet2Id = "wallet-receiver-" + System.currentTimeMillis() + "-2";

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
        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );

        // When - Get events for wallet2 (receiver)
        ResponseEntity<WalletHistoryResponse> eventsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id + "/events?page=0&size=20", WalletHistoryResponse.class);

        // Then - Should include wallet opened and transfer events
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse events = eventsResponse.getBody();
        assertThat(events).isNotNull();
        assertThat(events.events()).hasSize(2); // WalletOpened + MoneyTransferred (as receiver)

        // Verify event types
        assertThat(events.events().stream().map(WalletEventDTO::eventType))
                .containsExactlyInAnyOrder("WalletOpened", "MoneyTransferred");
    }

    @Test
    @DisplayName("Should handle pagination with cursor")
    void shouldHandlePaginationWithCursor() {
        // Given - Create wallet with unique ID and multiple deposits
        String walletId = "wallet-pagination-" + System.currentTimeMillis();
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
        ResponseEntity<WalletHistoryResponse> firstPageResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/events?page=0&size=2", WalletHistoryResponse.class);

        // Then - Should return 2 events with pagination info
        assertThat(firstPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse firstPage = firstPageResponse.getBody();
        assertThat(firstPage).isNotNull();
        assertThat(firstPage.events()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();

        // When - Get second page
        ResponseEntity<WalletHistoryResponse> secondPageResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/events?page=1&size=2",
                WalletHistoryResponse.class);

        // Then - Should return remaining events
        assertThat(secondPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse secondPage = secondPageResponse.getBody();
        assertThat(secondPage).isNotNull();
        assertThat(secondPage.events()).hasSize(2); // Remaining events
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should return empty events for non-existent wallet")
    void shouldReturnEmptyEventsForNonExistentWallet() {
        // When - Get events for non-existent wallet
        ResponseEntity<WalletHistoryResponse> eventsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/non-existent/events?page=0&size=20", WalletHistoryResponse.class);

        // Then - Should return empty list
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse events = eventsResponse.getBody();
        assertThat(events).isNotNull();
        assertThat(events.events()).isEmpty();
        assertThat(events.hasNext()).isFalse();
    }
}
