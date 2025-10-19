package integration.api.controller;

import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import testutils.AbstractCrabletTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DepositController using TestRestTemplate.
 * Tests deposit operations with real HTTP requests and database.
 */
class DepositControllerIT extends AbstractCrabletTest {

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
    @DisplayName("Should successfully deposit money into a wallet")
    void shouldSuccessfullyDepositMoney() {
        // Arrange: Create a wallet first
        String walletId = "deposit-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Deposit money
        DepositRequest depositRequest = new DepositRequest("deposit-1", 500, "Bonus payment");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", depositRequest, Void.class
        );

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance increased
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1500); // 1000 + 500
    }

    @Test
    @DisplayName("Should handle deposit to non-existent wallet")
    void shouldHandleDepositToNonExistentWallet() {
        // Arrange
        String nonExistentWalletId = "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + nonExistentWalletId;

        DepositRequest depositRequest = new DepositRequest("deposit-1", 500, "Bonus payment");

        // Act
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", depositRequest, Void.class
        );

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle invalid deposit amount")
    void shouldHandleInvalidDepositAmount() {
        // Arrange: Create a wallet first
        String walletId = "invalid-deposit-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to deposit negative amount
        DepositRequest depositRequest = new DepositRequest("deposit-1", -100, "Invalid deposit");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", depositRequest, Void.class
        );

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should handle zero deposit amount")
    void shouldHandleZeroDepositAmount() {
        // Arrange: Create a wallet first
        String walletId = "zero-deposit-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to deposit zero amount
        DepositRequest depositRequest = new DepositRequest("deposit-1", 0, "Zero deposit");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", depositRequest, Void.class
        );

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should validate request body")
    void shouldValidateRequestBody() {
        // Arrange: Create a wallet first
        String walletId = "validation-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to deposit with invalid request body (empty depositId)
        DepositRequest invalidRequest = new DepositRequest("", 100, "");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", invalidRequest, Void.class
        );

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should handle deposit idempotency")
    void shouldHandleDepositIdempotency() {
        // Arrange: Create a wallet first
        String walletId = "idempotent-deposit-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: First deposit
        DepositRequest depositRequest = new DepositRequest("idempotent-deposit-1", 200, "First deposit");
        ResponseEntity<Void> firstDepositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", depositRequest, Void.class
        );
        assertThat(firstDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify first deposit
        ResponseEntity<WalletResponse> afterFirstDeposit = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(afterFirstDeposit.getBody()).isNotNull();
        assertThat(afterFirstDeposit.getBody().balance()).isEqualTo(1200); // 1000 + 200

        // Act: Second deposit with same depositId (should be idempotent)
        ResponseEntity<Void> secondDepositResponse = restTemplate.postForEntity(
                walletUrl + "/deposit", depositRequest, Void.class
        );
        assertThat(secondDepositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Assert: Balance should be unchanged (idempotent)
        ResponseEntity<WalletResponse> afterSecondDeposit = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(afterSecondDeposit.getBody()).isNotNull();
        assertThat(afterSecondDeposit.getBody().balance()).isEqualTo(1200); // Unchanged
    }

    @Test
    @DisplayName("Should handle multiple deposits correctly")
    void shouldHandleMultipleDepositsCorrectly() {
        // Arrange: Create a wallet first
        String walletId = "multiple-deposits-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Make multiple deposits
        DepositRequest deposit1 = new DepositRequest("deposit-1", 200, "First deposit");
        DepositRequest deposit2 = new DepositRequest("deposit-2", 300, "Second deposit");
        DepositRequest deposit3 = new DepositRequest("deposit-3", 100, "Third deposit");

        ResponseEntity<Void> response1 = restTemplate.postForEntity(walletUrl + "/deposit", deposit1, Void.class);
        ResponseEntity<Void> response2 = restTemplate.postForEntity(walletUrl + "/deposit", deposit2, Void.class);
        ResponseEntity<Void> response3 = restTemplate.postForEntity(walletUrl + "/deposit", deposit3, Void.class);

        // Assert: All deposits successful
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify final balance
        ResponseEntity<WalletResponse> finalWalletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(finalWalletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalWalletResponse.getBody()).isNotNull();
        assertThat(finalWalletResponse.getBody().balance()).isEqualTo(1600); // 1000 + 200 + 300 + 100
    }
}
