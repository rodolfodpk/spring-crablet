package wallets.integration.api.controller;

import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletResponse;
import com.wallets.features.transfer.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import wallets.integration.AbstractWalletIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TransferController using TestRestTemplate.
 * Tests transfer operations with real HTTP requests and database.
 */
class TransferControllerIT extends AbstractWalletIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/wallets";
    }

    @Test
    @DisplayName("Should successfully transfer money between wallets")
    void shouldSuccessfullyTransferMoney() {
        // Arrange: Create both wallets
        String wallet1Id = "transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Transfer money
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", wallet1Id, wallet2Id, 300, "Payment"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify balances after transfer
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(700); // 1000 - 300
        assertThat(wallet2Response.getBody().balance()).isEqualTo(800); // 500 + 300
    }

    @Test
    @DisplayName("Should handle transfer from non-existent source wallet")
    void shouldHandleTransferFromNonExistentSourceWallet() {
        // Arrange: Create only destination wallet
        String wallet2Id = "transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);
        String nonExistentWalletId = "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Try to transfer from non-existent wallet
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", nonExistentWalletId, wallet2Id, 100, "Payment"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify destination wallet balance unchanged
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody().balance()).isEqualTo(500); // Unchanged
    }

    @Test
    @DisplayName("Should handle transfer to non-existent destination wallet")
    void shouldHandleTransferToNonExistentDestinationWallet() {
        // Arrange: Create only source wallet
        String wallet1Id = "transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String nonExistentWalletId = "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);

        // Act: Try to transfer to non-existent wallet
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", wallet1Id, nonExistentWalletId, 100, "Payment"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify source wallet balance unchanged
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should handle insufficient funds")
    void shouldHandleInsufficientFunds() {
        // Arrange: Create both wallets
        String wallet1Id = "insufficient-funds-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "insufficient-funds-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 100);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Try to transfer more than available
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", wallet1Id, wallet2Id, 200, "Payment"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balances unchanged
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(100); // Unchanged
        assertThat(wallet2Response.getBody().balance()).isEqualTo(500); // Unchanged
    }

    @Test
    @DisplayName("Should handle invalid transfer amount")
    void shouldHandleInvalidTransferAmount() {
        // Arrange: Create both wallets
        String wallet1Id = "invalid-transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "invalid-transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Try to transfer negative amount
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", wallet1Id, wallet2Id, -100, "Invalid transfer"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balances unchanged
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(1000); // Unchanged
        assertThat(wallet2Response.getBody().balance()).isEqualTo(500); // Unchanged
    }

    @Test
    @DisplayName("Should handle zero transfer amount")
    void shouldHandleZeroTransferAmount() {
        // Arrange: Create both wallets
        String wallet1Id = "zero-transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "zero-transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Try to transfer zero amount
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", wallet1Id, wallet2Id, 0, "Zero transfer"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balances unchanged
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(1000); // Unchanged
        assertThat(wallet2Response.getBody().balance()).isEqualTo(500); // Unchanged
    }

    @Test
    @DisplayName("Should handle transfer with exact balance")
    void shouldHandleTransferWithExactBalance() {
        // Arrange: Create both wallets
        String wallet1Id = "exact-balance-transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "exact-balance-transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 500);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 200);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Transfer exact balance
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", wallet1Id, wallet2Id, 500, "Full transfer"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify balances after transfer
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(0); // 500 - 500
        assertThat(wallet2Response.getBody().balance()).isEqualTo(700); // 200 + 500
    }

    @Test
    @DisplayName("Should validate request body")
    void shouldValidateRequestBody() {
        // Arrange: Create both wallets
        String wallet1Id = "validation-transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "validation-transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Try to transfer with invalid request body (empty IDs)
        TransferRequest invalidRequest = new TransferRequest("", "", "", 100, "");
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", invalidRequest, Void.class
        );

        // Assert
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balances unchanged
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet1Response.getBody().balance()).isEqualTo(1000); // Unchanged
        assertThat(wallet2Response.getBody().balance()).isEqualTo(500); // Unchanged
    }

    @Test
    @DisplayName("Should handle transfer (no longer idempotent)")
    void shouldHandleTransferIdempotency() {
        // Arrange: Create both wallets
        String wallet1Id = "idempotent-transfer-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "idempotent-transfer-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: First transfer
        TransferRequest transferRequest = new TransferRequest(
                "idempotent-transfer-1", wallet1Id, wallet2Id, 200, "First transfer"
        );
        ResponseEntity<Void> firstTransferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );
        assertThat(firstTransferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify first transfer
        ResponseEntity<WalletResponse> wallet1AfterFirst = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2AfterFirst = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1AfterFirst.getBody()).isNotNull();
        assertThat(wallet2AfterFirst.getBody()).isNotNull();
        assertThat(wallet1AfterFirst.getBody().balance()).isEqualTo(800); // 1000 - 200
        assertThat(wallet2AfterFirst.getBody().balance()).isEqualTo(700); // 500 + 200

        // Act: Second transfer with same transferId (no longer idempotent)
        ResponseEntity<Void> secondTransferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );
        assertThat(secondTransferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Assert: Balances should reflect both transfers (no longer idempotent)
        ResponseEntity<WalletResponse> wallet1AfterSecond = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2AfterSecond = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(wallet1AfterSecond.getBody()).isNotNull();
        assertThat(wallet2AfterSecond.getBody()).isNotNull();
        assertThat(wallet1AfterSecond.getBody().balance()).isEqualTo(600); // 1000 - 200 - 200
        assertThat(wallet2AfterSecond.getBody().balance()).isEqualTo(900); // 500 + 200 + 200
    }

    @Test
    @DisplayName("Should handle multiple transfers correctly")
    void shouldHandleMultipleTransfersCorrectly() {
        // Arrange: Create both wallets
        String wallet1Id = "multiple-transfers-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "multiple-transfers-wallet2-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 500);

        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);

        // Act: Make multiple transfers
        TransferRequest transfer1 = new TransferRequest("transfer-1", wallet1Id, wallet2Id, 200, "First transfer");
        TransferRequest transfer2 = new TransferRequest("transfer-2", wallet1Id, wallet2Id, 300, "Second transfer");
        TransferRequest transfer3 = new TransferRequest("transfer-3", wallet1Id, wallet2Id, 100, "Third transfer");

        ResponseEntity<Void> response1 = restTemplate.postForEntity(baseUrl + "/transfer", transfer1, Void.class);
        ResponseEntity<Void> response2 = restTemplate.postForEntity(baseUrl + "/transfer", transfer2, Void.class);
        ResponseEntity<Void> response3 = restTemplate.postForEntity(baseUrl + "/transfer", transfer3, Void.class);

        // Assert: All transfers successful
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify final balances
        ResponseEntity<WalletResponse> finalWallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> finalWallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );

        assertThat(finalWallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalWallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalWallet1Response.getBody()).isNotNull();
        assertThat(finalWallet2Response.getBody()).isNotNull();
        assertThat(finalWallet1Response.getBody().balance()).isEqualTo(400); // 1000 - 200 - 300 - 100
        assertThat(finalWallet2Response.getBody().balance()).isEqualTo(1100); // 500 + 200 + 300 + 100
    }

    @Test
    @DisplayName("Should handle transfer between same wallet")
    void shouldHandleTransferBetweenSameWallet() {
        // Arrange: Create a wallet
        String walletId = "same-wallet-transfer-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(baseUrl + "/" + walletId, walletRequest);

        // Act: Try to transfer to the same wallet
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", walletId, walletId, 200, "Self transfer"
        );
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", transferRequest, Void.class
        );

        // Assert: Should be rejected (invalid operation)
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(
                baseUrl + "/" + walletId, WalletResponse.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }
}
