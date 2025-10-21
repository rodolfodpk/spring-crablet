package integration.api.controller;

import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletResponse;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WithdrawController using TestRestTemplate.
 * Tests withdrawal operations with real HTTP requests and database.
 */
class WithdrawControllerIT extends AbstractCrabletTest {

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
    @DisplayName("Should successfully withdraw money from a wallet")
    void shouldSuccessfullyWithdrawMoney() {
        // Arrange: Create a wallet first
        String walletId = "withdraw-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Withdraw money
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", 300, "Shopping");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance decreased
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(700); // 1000 - 300
    }

    @Test
    @DisplayName("Should handle withdrawal from non-existent wallet")
    void shouldHandleWithdrawalFromNonExistentWallet() {
        // Arrange
        String nonExistentWalletId = "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + nonExistentWalletId;

        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", 300, "Shopping");

        // Act
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle insufficient funds")
    void shouldHandleInsufficientFunds() {
        // Arrange: Create a wallet with low balance
        String walletId = "insufficient-funds-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 100);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to withdraw more than available
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", 200, "Overdraft");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(100); // Unchanged
    }

    @Test
    @DisplayName("Should handle invalid withdrawal amount")
    void shouldHandleInvalidWithdrawalAmount() {
        // Arrange: Create a wallet first
        String walletId = "invalid-withdraw-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to withdraw negative amount
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", -100, "Invalid withdrawal");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should handle zero withdrawal amount")
    void shouldHandleZeroWithdrawalAmount() {
        // Arrange: Create a wallet first
        String walletId = "zero-withdraw-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to withdraw zero amount
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", 0, "Zero withdrawal");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should handle withdrawal with exact balance")
    void shouldHandleWithdrawalWithExactBalance() {
        // Arrange: Create a wallet with exact balance
        String walletId = "exact-balance-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 500);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Withdraw exact balance
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", 500, "Full withdrawal");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance is zero
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(0); // 500 - 500
    }

    @Test
    @DisplayName("Should validate request body")
    void shouldValidateRequestBody() {
        // Arrange: Create a wallet first
        String walletId = "validation-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Try to withdraw with invalid request body (empty withdrawalId)
        WithdrawRequest invalidRequest = new WithdrawRequest("", 100, "");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", invalidRequest, Void.class
        );

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet balance unchanged
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();
        assertThat(walletResponse.getBody().balance()).isEqualTo(1000); // Unchanged
    }

    @Test
    @DisplayName("Should handle withdrawal (no longer idempotent)")
    void shouldHandleWithdrawalIdempotency() {
        // Arrange: Create a wallet first
        String walletId = "idempotent-withdraw-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: First withdrawal
        WithdrawRequest withdrawRequest = new WithdrawRequest("idempotent-withdrawal-1", 200, "First withdrawal");
        ResponseEntity<Void> firstWithdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );
        assertThat(firstWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify first withdrawal
        ResponseEntity<WalletResponse> afterFirstWithdraw = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(afterFirstWithdraw.getBody()).isNotNull();
        assertThat(afterFirstWithdraw.getBody().balance()).isEqualTo(800); // 1000 - 200

        // Act: Second withdrawal with same withdrawalId (no longer idempotent)
        ResponseEntity<Void> secondWithdrawResponse = restTemplate.postForEntity(
                walletUrl + "/withdraw", withdrawRequest, Void.class
        );
        assertThat(secondWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Assert: Balance should reflect both withdrawals (no longer idempotent)
        ResponseEntity<WalletResponse> afterSecondWithdraw = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(afterSecondWithdraw.getBody()).isNotNull();
        assertThat(afterSecondWithdraw.getBody().balance()).isEqualTo(600); // 1000 - 200 - 200
    }

    @Test
    @DisplayName("Should handle multiple withdrawals correctly")
    void shouldHandleMultipleWithdrawalsCorrectly() {
        // Arrange: Create a wallet with sufficient balance
        String walletId = "multiple-withdrawals-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Make multiple withdrawals
        WithdrawRequest withdraw1 = new WithdrawRequest("withdrawal-1", 200, "First withdrawal");
        WithdrawRequest withdraw2 = new WithdrawRequest("withdrawal-2", 300, "Second withdrawal");
        WithdrawRequest withdraw3 = new WithdrawRequest("withdrawal-3", 100, "Third withdrawal");

        ResponseEntity<Void> response1 = restTemplate.postForEntity(walletUrl + "/withdraw", withdraw1, Void.class);
        ResponseEntity<Void> response2 = restTemplate.postForEntity(walletUrl + "/withdraw", withdraw2, Void.class);
        ResponseEntity<Void> response3 = restTemplate.postForEntity(walletUrl + "/withdraw", withdraw3, Void.class);

        // Assert: All withdrawals successful
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify final balance
        ResponseEntity<WalletResponse> finalWalletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(finalWalletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalWalletResponse.getBody()).isNotNull();
        assertThat(finalWalletResponse.getBody().balance()).isEqualTo(400); // 1000 - 200 - 300 - 100
    }

    @Test
    @DisplayName("Should handle withdrawal after deposit")
    void shouldHandleWithdrawalAfterDeposit() {
        // Arrange: Create a wallet
        String walletId = "deposit-then-withdraw-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 500);
        restTemplate.put(walletUrl, walletRequest);

        // Act: Make a deposit first
        com.wallets.features.deposit.DepositRequest depositRequest = new com.wallets.features.deposit.DepositRequest("deposit-1", 300, "Deposit before withdrawal");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(walletUrl + "/deposit", depositRequest, Void.class);
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify balance after deposit
        ResponseEntity<WalletResponse> afterDeposit = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(afterDeposit.getBody()).isNotNull();
        assertThat(afterDeposit.getBody().balance()).isEqualTo(800); // 500 + 300

        // Act: Then make a withdrawal
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdrawal-1", 200, "Withdrawal after deposit");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(walletUrl + "/withdraw", withdrawRequest, Void.class);

        // Assert
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify final balance
        ResponseEntity<WalletResponse> finalWalletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(finalWalletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalWalletResponse.getBody()).isNotNull();
        assertThat(finalWalletResponse.getBody().balance()).isEqualTo(600); // 800 - 200
    }
}
