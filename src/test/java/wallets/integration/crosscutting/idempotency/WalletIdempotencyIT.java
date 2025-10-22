package wallets.integration.crosscutting.idempotency;

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
import wallets.integration.AbstractWalletIntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for wallet idempotency scenarios.
 * <p>
 * Tests idempotent behavior:
 * 1. Duplicate wallet creation (same wallet ID) - STILL idempotent
 * 2. Duplicate deposit (same deposit ID) - NO LONGER idempotent
 * 3. Duplicate withdrawal (same withdrawal ID) - NO LONGER idempotent
 * 4. Duplicate transfer (same transfer ID) - NO LONGER idempotent
 * 5. Verify balances unchanged on duplicate operations (for wallet creation only)
 * 6. Verify HTTP responses: 200 OK for wallet creation, 201 CREATED for operations
 */
class WalletIdempotencyIT extends AbstractWalletIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should handle duplicate wallet creation idempotently")
    void shouldHandleDuplicateWalletCreationIdempotently() {
        String walletId = "idempotent-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest = new OpenWalletRequest("Alice", 1000);

        // First creation
        ResponseEntity<Void> firstResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second creation with same wallet ID (should be idempotent)
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

        // Verify wallet state is unchanged
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(1000);
        assertThat(walletBody.get("owner")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Should handle duplicate deposit (no longer idempotent)")
    void shouldHandleDuplicateDepositIdempotently() {
        String walletId = "idempotent-" + UUID.randomUUID().toString().substring(0, 8);
        String depositId = "deposit-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Bob", 500);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First deposit
        DepositRequest depositRequest = new DepositRequest(depositId, 200, "First deposit");
        ResponseEntity<Void> firstDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(firstDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second deposit with same deposit ID (no longer idempotent)
        ResponseEntity<Void> secondDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance reflects both deposits (500 + 200 + 200 = 900)
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(900); // Both deposits applied
    }

    @Test
    @DisplayName("Should handle duplicate withdrawal (no longer idempotent)")
    void shouldHandleDuplicateWithdrawalIdempotently() {
        String walletId = "idempotent-" + UUID.randomUUID().toString().substring(0, 8);
        String withdrawalId = "withdrawal-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Charlie", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First withdrawal
        WithdrawRequest withdrawRequest = new WithdrawRequest(withdrawalId, 300, "First withdrawal");
        ResponseEntity<Void> firstWithdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Void.class
        );
        assertThat(firstWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second withdrawal with same withdrawal ID (no longer idempotent)
        ResponseEntity<Void> secondWithdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance reflects both withdrawals (1000 - 300 - 300 = 400)
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(400); // Both withdrawals applied
    }

    @Test
    @DisplayName("Should handle duplicate transfer (no longer idempotent)")
    void shouldHandleDuplicateTransferIdempotently() {
        String wallet1Id = "idempotent-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "idempotent-2-" + UUID.randomUUID().toString().substring(0, 8);
        String transferId = "transfer-" + UUID.randomUUID().toString().substring(0, 8);

        // Create both wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("David", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Eve", 500);

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

        // First transfer
        TransferRequest transferRequest = new TransferRequest(transferId, wallet1Id, wallet2Id, 200, "First transfer");
        ResponseEntity<Void> firstTransferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );
        assertThat(firstTransferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second transfer with same transfer ID (no longer idempotent)
        ResponseEntity<Void> secondTransferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondTransferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balances reflect both transfers
        ResponseEntity<Map> wallet1Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                Map.class
        );
        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> wallet1Body = wallet1Response.getBody();
        assertThat(wallet1Body).isNotNull();
        assertThat(wallet1Body.get("balance")).isEqualTo(600); // 1000 - 200 - 200, both transfers applied

        ResponseEntity<Map> wallet2Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                Map.class
        );
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> wallet2Body = wallet2Response.getBody();
        assertThat(wallet2Body).isNotNull();
        assertThat(wallet2Body.get("balance")).isEqualTo(900); // 500 + 200 + 200, both transfers applied
    }

    @Test
    @DisplayName("Should handle operation ID collision scenarios")
    void shouldHandleOperationIdCollisionScenarios() {
        String walletId = "idempotent-" + UUID.randomUUID().toString().substring(0, 8);
        String operationId = "collision-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Frank", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First operation - deposit
        DepositRequest depositRequest = new DepositRequest(operationId, 100, "Deposit with collision ID");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second operation - withdrawal with same ID (different operation type, so NOT idempotent)
        WithdrawRequest withdrawRequest = new WithdrawRequest(operationId, 50, "Withdrawal with collision ID");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Void.class
        );

        // Should return 201 CREATED (new operation, not idempotent)
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance reflects both operations (1000 + 100 - 50 = 1050)
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(1050); // Both deposit and withdrawal applied
    }

    @Test
    @DisplayName("Should simulate network retry scenarios (no longer idempotent)")
    void shouldSimulateNetworkRetryScenarios() {
        String walletId = "idempotent-" + UUID.randomUUID().toString().substring(0, 8);
        String depositId = "retry-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Grace", 500);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Simulate network retry - same request sent multiple times
        DepositRequest depositRequest = new DepositRequest(depositId, 150, "Network retry simulation");

        // First attempt (simulates successful request)
        ResponseEntity<Void> firstAttempt = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(firstAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second attempt (simulates client retry due to network timeout)
        ResponseEntity<Void> secondAttempt = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Third attempt (simulates another retry)
        ResponseEntity<Void> thirdAttempt = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(thirdAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify wallet balance reflects all deposits (500 + 150 + 150 + 150 = 950)
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> walletBody = walletResponse.getBody();
        assertThat(walletBody).isNotNull();
        assertThat(walletBody.get("balance")).isEqualTo(950); // All deposits applied despite multiple requests
    }
}

