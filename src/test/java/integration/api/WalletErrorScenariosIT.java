package integration.api;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for wallet error scenarios.
 * 
 * Tests error scenarios:
 * 1. Insufficient funds for withdrawal
 * 2. Insufficient funds for transfer
 * 3. Duplicate wallet creation (idempotent)
 * 4. Wallet not found for operations
 * 5. Wallet not found for queries
 */
class WalletErrorScenariosIT extends AbstractCrabletTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should handle insufficient funds for withdrawal")
    void shouldHandleInsufficientFundsForWithdrawal() {
        // Create wallet with small balance
        String walletId = "error-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest = new OpenWalletRequest("user1", 50);
        
        ResponseEntity<Void> openResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/wallets/" + walletId,
            HttpMethod.PUT,
            new HttpEntity<>(openRequest),
            Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to withdraw more than available
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 100, "Large withdrawal");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
            withdrawRequest,
            Map.class
        );
        
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> errorBody = withdrawResponse.getBody();
        assertThat(errorBody).isNotNull();
        assertThat(errorBody.get("error")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) errorBody.get("error");
        assertThat(error.get("code")).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("Should handle insufficient funds for transfer")
    void shouldHandleInsufficientFundsForTransfer() {
        // Create two wallets
        String wallet1Id = "error-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "error-" + UUID.randomUUID().toString().substring(0, 8);
        
        OpenWalletRequest openRequest1 = new OpenWalletRequest("user1", 50);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("user2", 100);
        
        ResponseEntity<Void> openResponse1 = restTemplate.exchange(
            "http://localhost:" + port + "/api/wallets/" + wallet1Id,
            HttpMethod.PUT,
            new HttpEntity<>(openRequest1),
            Void.class
        );
        assertThat(openResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> openResponse2 = restTemplate.exchange(
            "http://localhost:" + port + "/api/wallets/" + wallet2Id,
            HttpMethod.PUT,
            new HttpEntity<>(openRequest2),
            Void.class
        );
        assertThat(openResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to transfer more than available from wallet 1
        TransferRequest transferRequest = new TransferRequest("transfer-1", wallet1Id, wallet2Id, 100, "Large transfer");
        ResponseEntity<Map> transferResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/wallets/transfer",
            transferRequest,
            Map.class
        );
        
        // Transfer fails due to insufficient funds
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle duplicate wallet creation (idempotent)")
    void shouldHandleDuplicateWalletCreation() {
        String walletId = "error-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest = new OpenWalletRequest("user1", 1000);
        
        // First creation
        ResponseEntity<Void> firstResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/wallets/" + walletId,
            HttpMethod.PUT,
            new HttpEntity<>(openRequest),
            Void.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second creation (should be idempotent)
        ResponseEntity<Map> secondResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/wallets/" + walletId,
            HttpMethod.PUT,
            new HttpEntity<>(openRequest),
            Map.class
        );
        
        // Should return 200 OK with message (idempotent behavior)
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> responseBody = secondResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("message")).isNotNull();
        assertThat(responseBody.get("message").toString()).contains("Wallet already exists");
    }

    @Test
    @DisplayName("Should handle wallet not found for operations")
    void shouldHandleWalletNotFoundForOperations() {
        String nonExistentWalletId = "non-existent-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Try to deposit to non-existent wallet
        DepositRequest depositRequest = new DepositRequest("deposit-1", 100, "Deposit to non-existent wallet");
        ResponseEntity<Map> depositResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/wallets/" + nonExistentWalletId + "/deposit",
            depositRequest,
            Map.class
        );
        
        // Deposit fails for non-existent wallet
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle wallet not found for queries")
    void shouldHandleWalletNotFoundForQueries() {
        String nonExistentWalletId = "non-existent-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Try to query events for non-existent wallet
        ResponseEntity<Map> eventsResponse = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/wallets/" + nonExistentWalletId + "/events?page=0&size=20",
            Map.class
        );
        
        // Query succeeds but returns empty events (no validation implemented yet)
        assertThat(eventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> responseBody = eventsResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("events")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Object> events = (List<Object>) responseBody.get("events");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should handle invalid request parameters")
    void shouldHandleInvalidRequestParameters() {
        String walletId = "error-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Try to open wallet with negative balance
        OpenWalletRequest invalidRequest = new OpenWalletRequest("user1", -100);
        ResponseEntity<Map> openResponse = restTemplate.exchange(
            "http://localhost:" + port + "/api/wallets/" + walletId,
            HttpMethod.PUT,
            new HttpEntity<>(invalidRequest),
            Map.class
        );
        
        // Request fails with validation error
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = openResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isEqualTo("Validation failed");
        assertThat(responseBody.get("message")).isEqualTo("Invalid request parameters");
        
        // Check field errors
        Map<String, String> fieldErrors = (Map<String, String>) responseBody.get("fields");
        assertThat(fieldErrors).isNotNull();
        assertThat(fieldErrors.get("initialBalance")).contains("cannot be negative");
    }
}
