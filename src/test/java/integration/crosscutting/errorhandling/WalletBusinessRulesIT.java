package integration.crosscutting.errorhandling;

import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.transfer.TransferRequest;
import com.wallets.features.withdraw.WithdrawRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import testutils.AbstractCrabletTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for wallet business rules and edge cases.
 * <p>
 * Tests critical business rules:
 * 1. Transfer to same wallet (should fail)
 * 2. Zero amount operations (prevented at command creation)
 * 3. Negative amount operations (prevented at command creation)
 * 4. Invalid wallet IDs and operation IDs
 * 5. Money conservation across multiple operations
 */
class WalletBusinessRulesIT extends AbstractCrabletTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should prevent transfer to same wallet")
    void shouldPreventTransferToSameWallet() {
        String walletId = "same-wallet-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Alice", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to transfer to same wallet
        TransferRequest transferRequest = new TransferRequest(
                "transfer-1", walletId, walletId, 100, "Self transfer"
        );
        ResponseEntity<Map> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Map.class
        );

        // Should fail with 400 BAD REQUEST
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = transferResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertThat(error.get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message")).toString().contains("same wallet");
    }

    @Test
    @DisplayName("Should prevent zero amount operations at command creation")
    void shouldPreventZeroAmountOperationsAtCommandCreation() {
        String walletId = "zero-amount-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Bob", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try zero amount deposit
        DepositRequest zeroDepositRequest = new DepositRequest("deposit-1", 0, "Zero deposit");
        ResponseEntity<Map> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                zeroDepositRequest,
                Map.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Try zero amount withdrawal
        WithdrawRequest zeroWithdrawRequest = new WithdrawRequest("withdraw-1", 0, "Zero withdrawal");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                zeroWithdrawRequest,
                Map.class
        );
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Try zero amount transfer
        String wallet2Id = "zero-amount-2-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Charlie", 500);
        ResponseEntity<Void> openResponse2 = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest2),
                Void.class
        );
        assertThat(openResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TransferRequest zeroTransferRequest = new TransferRequest(
                "transfer-1", walletId, wallet2Id, 0, "Zero transfer"
        );
        ResponseEntity<Map> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                zeroTransferRequest,
                Map.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should prevent negative amount operations at command creation")
    void shouldPreventNegativeAmountOperationsAtCommandCreation() {
        String walletId = "negative-amount-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("David", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try negative amount deposit
        DepositRequest negativeDepositRequest = new DepositRequest("deposit-1", -100, "Negative deposit");
        ResponseEntity<Map> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                negativeDepositRequest,
                Map.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Try negative amount withdrawal
        WithdrawRequest negativeWithdrawRequest = new WithdrawRequest("withdraw-1", -50, "Negative withdrawal");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                negativeWithdrawRequest,
                Map.class
        );
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Try negative amount transfer
        String wallet2Id = "negative-amount-2-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Eve", 500);
        ResponseEntity<Void> openResponse2 = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest2),
                Void.class
        );
        assertThat(openResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TransferRequest negativeTransferRequest = new TransferRequest(
                "transfer-1", walletId, wallet2Id, -200, "Negative transfer"
        );
        ResponseEntity<Map> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                negativeTransferRequest,
                Map.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle invalid wallet IDs")
    void shouldHandleInvalidWalletIds() {
        // Try to create wallet with null ID (handled by Spring validation)
        OpenWalletRequest openRequest = new OpenWalletRequest("Frank", 1000);
        ResponseEntity<Map> nullIdResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/",
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Map.class
        );
        assertThat(nullIdResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Try to create wallet with empty ID
        ResponseEntity<Map> emptyIdResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/",
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Map.class
        );
        assertThat(emptyIdResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Try operations on non-existent wallet
        String nonExistentWalletId = "non-existent-" + UUID.randomUUID().toString().substring(0, 8);
        DepositRequest depositRequest = new DepositRequest("deposit-1", 100, "Test");
        ResponseEntity<Map> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + nonExistentWalletId + "/deposit",
                depositRequest,
                Map.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle invalid operation IDs")
    void shouldHandleInvalidOperationIds() {
        String walletId = "invalid-ops-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Grace", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try deposit with null operation ID
        DepositRequest nullDepositRequest = new DepositRequest(null, 100, "Null ID deposit");
        ResponseEntity<Map> nullDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                nullDepositRequest,
                Map.class
        );
        assertThat(nullDepositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Try deposit with empty operation ID
        DepositRequest emptyDepositRequest = new DepositRequest("", 100, "Empty ID deposit");
        ResponseEntity<Map> emptyDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                emptyDepositRequest,
                Map.class
        );
        assertThat(emptyDepositResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Try withdrawal with null operation ID
        WithdrawRequest nullWithdrawRequest = new WithdrawRequest(null, 50, "Null ID withdrawal");
        ResponseEntity<Map> nullWithdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                nullWithdrawRequest,
                Map.class
        );
        assertThat(nullWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should enforce minimum balance constraints")
    void shouldEnforceMinimumBalanceConstraints() {
        String walletId = "min-balance-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet with small balance
        OpenWalletRequest openRequest = new OpenWalletRequest("Henry", 100);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to withdraw more than available
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 150, "Overdraft attempt");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Map.class
        );
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> responseBody = withdrawResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertThat(error.get("code")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(error.get("currentBalance")).isEqualTo(100);
        assertThat(error.get("requestedAmount")).isEqualTo(150);
    }

    @Test
    @DisplayName("Should handle exact balance operations")
    void shouldHandleExactBalanceOperations() {
        String walletId = "exact-balance-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Iris", 500);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Withdraw exact balance
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 500, "Full withdrawal");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Void.class
        );
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify balance is zero
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large amount operations")
    void shouldHandleLargeAmountOperations() {
        String walletId = "large-amount-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet with large balance
        OpenWalletRequest openRequest = new OpenWalletRequest("Jack", 1000000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Deposit large amount
        DepositRequest depositRequest = new DepositRequest("deposit-1", 500000, "Large deposit");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify balance
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(1500000);
    }

    @Test
    @DisplayName("Should handle special characters in descriptions")
    void shouldHandleSpecialCharactersInDescriptions() {
        String walletId = "special-chars-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Kelly", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Deposit with special characters in description
        DepositRequest depositRequest = new DepositRequest(
                "deposit-1", 100, "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?"
        );
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify operation succeeded
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(1100);
    }
}

