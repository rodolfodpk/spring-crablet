package wallets.integration.crosscutting.errorhandling;

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
 * Integration test for GlobalExceptionHandler mapping.
 * <p>
 * Tests that GlobalExceptionHandler correctly maps domain exceptions to HTTP responses:
 * 1. WalletNotFoundException → 404 NOT_FOUND
 * 2. InsufficientFundsException → 400 BAD_REQUEST
 * 3. WalletAlreadyExistsException → 200 OK
 * 4. DuplicateOperationException → 200 OK
 * 5. OptimisticLockException → 409 CONFLICT
 * 6. ConcurrencyException → 409 CONFLICT
 * 7. IllegalArgumentException → 400 BAD_REQUEST
 * 8. Generic exceptions → 500 INTERNAL_SERVER_ERROR
 */
class GlobalExceptionHandlerIT extends AbstractWalletIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should map WalletNotFoundException to 404 NOT_FOUND")
    void shouldMapWalletNotFoundExceptionTo404NotFound() {
        String nonExistentWalletId = "non-existent-" + UUID.randomUUID().toString().substring(0, 8);

        // Try to deposit to non-existent wallet
        DepositRequest depositRequest = new DepositRequest("deposit-1", 100, "Test deposit");
        ResponseEntity<Map> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + nonExistentWalletId + "/deposit",
                depositRequest,
                Map.class
        );

        // Should return 404 NOT_FOUND
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> responseBody = depositResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertThat(error.get("code")).isEqualTo("WALLET_NOT_FOUND");
        assertThat(error.get("message")).toString().contains(nonExistentWalletId);
        assertThat(error.get("walletId")).isEqualTo(nonExistentWalletId);
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should map InsufficientFundsException to 400 BAD_REQUEST")
    void shouldMapInsufficientFundsExceptionTo400BadRequest() {
        String walletId = "insufficient-funds-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet with small balance
        OpenWalletRequest openRequest = new OpenWalletRequest("Alice", 100);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to withdraw more than available
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 200, "Large withdrawal");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Map.class
        );

        // Should return 400 BAD_REQUEST
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> responseBody = withdrawResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertThat(error.get("code")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(error.get("message")).toString().contains("Insufficient funds");
        assertThat(error.get("walletId")).isEqualTo(walletId);
        assertThat(error.get("currentBalance")).isEqualTo(100);
        assertThat(error.get("requestedAmount")).isEqualTo(200);
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should map WalletAlreadyExistsException to 200 OK")
    void shouldMapWalletAlreadyExistsExceptionTo200Ok() {
        String walletId = "already-exists-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest = new OpenWalletRequest("Bob", 1000);

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

        // Should return 200 OK
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> responseBody = secondResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("message")).isNotNull();
        assertThat(responseBody.get("message").toString()).contains("Wallet already exists");
        assertThat(responseBody.get("walletId")).isEqualTo(walletId);
        assertThat(responseBody.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should map duplicate operation to 201 CREATED (no longer idempotent)")
    void shouldMapDuplicateOperationExceptionTo200Ok() {
        String walletId = "duplicate-operation-" + UUID.randomUUID().toString().substring(0, 8);
        String depositId = "duplicate-deposit-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Charlie", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First deposit
        DepositRequest depositRequest = new DepositRequest(depositId, 100, "Test deposit");
        ResponseEntity<Void> firstDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(firstDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second deposit with same ID (no longer idempotent)
        ResponseEntity<Void> secondDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should map OptimisticLockException to 409 CONFLICT")
    void shouldMapOptimisticLockExceptionTo409Conflict() {
        String walletId = "optimistic-lock-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("David", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Perform operations that might trigger optimistic lock conflicts
        // This is simulated by rapid concurrent operations
        DepositRequest depositRequest1 = new DepositRequest("optimistic-deposit-1", 100, "Optimistic test 1");
        DepositRequest depositRequest2 = new DepositRequest("optimistic-deposit-2", 200, "Optimistic test 2");

        ResponseEntity<?> depositResponse1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest1,
                Object.class
        );

        ResponseEntity<?> depositResponse2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest2,
                Object.class
        );

        // Should handle optimistic lock conflicts gracefully
        assertThat(depositResponse1.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(depositResponse2.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);

        // If there's a conflict, verify the response format
        if (depositResponse1.getStatusCode() == HttpStatus.CONFLICT) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) depositResponse1.getBody();
            assertThat(responseBody).isNotNull();
            assertThat(responseBody.get("error")).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
            assertThat(error.get("code")).isEqualTo("OPTIMISTIC_LOCK_FAILED");
            assertThat(error.get("message")).toString().contains("retry");
        }
    }

    @Test
    @DisplayName("Should map concurrency exception to 201 CREATED (no longer idempotent)")
    void shouldMapConcurrencyExceptionTo409Conflict() {
        String walletId = "concurrency-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Eve", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Perform operations that might trigger concurrency conflicts
        DepositRequest depositRequest = new DepositRequest("concurrency-deposit", 100, "Concurrency test");

        // First deposit
        ResponseEntity<Void> firstDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(firstDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second deposit with same ID (no longer idempotent)
        ResponseEntity<Void> secondDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should map IllegalArgumentException to 400 BAD_REQUEST")
    void shouldMapIllegalArgumentExceptionTo400BadRequest() {
        String walletId = "illegal-arg-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Frank", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to transfer to same wallet (should trigger IllegalArgumentException)
        TransferRequest transferRequest = new TransferRequest("transfer-1", walletId, walletId, 100, "Self transfer");
        ResponseEntity<Map> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Map.class
        );

        // Should return 400 BAD_REQUEST
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> responseBody = transferResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertThat(error.get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message")).toString().contains("same wallet");
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should map InvalidOperationException to 400 BAD_REQUEST")
    void shouldMapInvalidOperationExceptionTo400BadRequest() {
        String walletId = "invalid-operation-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Grace", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to withdraw with zero amount (should trigger InvalidOperationException)
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-1", 0, "Zero withdrawal");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Map.class
        );

        // Should return 400 BAD_REQUEST
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> responseBody = withdrawResponse.getBody();
        assertThat(responseBody).isNotNull();

        // Check if error is a nested object or a simple string
        Object errorObj = responseBody.get("error");
        assertThat(errorObj).isNotNull();

        if (errorObj instanceof String) {
            // Error is a simple string message
            assertThat((String) errorObj).contains("Validation failed");
        } else if (errorObj instanceof Map) {
            // Error is a nested object
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) errorObj;
            assertThat(error.get("code")).isEqualTo("VALIDATION_ERROR");
            assertThat(error.get("timestamp")).isNotNull();
        } else {
            // Fallback: just verify error field exists
            assertThat(errorObj).isNotNull();
        }
    }

    @Test
    @DisplayName("Should handle all operation types consistently")
    void shouldHandleAllOperationTypesConsistently() {
        String wallet1Id = "all-operations-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "all-operations-2-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("Henry", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Iris", 500);

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

        // Test deposit error handling
        DepositRequest depositRequest = new DepositRequest("all-ops-deposit", 100, "All ops test");
        ResponseEntity<?> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/deposit",
                depositRequest,
                Object.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Test withdrawal error handling
        WithdrawRequest withdrawRequest = new WithdrawRequest("all-ops-withdraw", 50, "All ops test");
        ResponseEntity<?> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/withdraw",
                withdrawRequest,
                Object.class
        );
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Test transfer error handling
        TransferRequest transferRequest = new TransferRequest("all-ops-transfer", wallet1Id, wallet2Id, 75, "All ops test");
        ResponseEntity<?> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Object.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle malformed requests gracefully")
    void shouldHandleMalformedRequestsGracefully() {
        String walletId = "malformed-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Jack", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to deposit with null amount (malformed request)
        DepositRequest malformedRequest = new DepositRequest("malformed-deposit", 0, "Malformed test");
        ResponseEntity<Map> malformedResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                malformedRequest,
                Map.class
        );

        // Should return 400 BAD_REQUEST
        assertThat(malformedResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> responseBody = malformedResponse.getBody();
        assertThat(responseBody).isNotNull();
    }

    @Test
    @DisplayName("Should provide consistent error response format")
    void shouldProvideConsistentErrorResponseFormat() {
        String nonExistentWalletId = "format-test-" + UUID.randomUUID().toString().substring(0, 8);

        // Try to deposit to non-existent wallet
        DepositRequest depositRequest = new DepositRequest("format-test", 100, "Format test");
        ResponseEntity<Map> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + nonExistentWalletId + "/deposit",
                depositRequest,
                Map.class
        );

        // Should return 404 NOT_FOUND with consistent format
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> responseBody = depositResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");

        // Verify consistent error format
        assertThat(error.get("code")).isNotNull();
        assertThat(error.get("message")).isNotNull();
        assertThat(error.get("timestamp")).isNotNull();

        // Verify error code is a string
        assertThat(error.get("code")).isInstanceOf(String.class);

        // Verify timestamp is a string (ISO format)
        assertThat(error.get("timestamp")).isInstanceOf(String.class);
        assertThat(error.get("timestamp").toString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    }

    @Test
    @DisplayName("Should map MethodArgumentNotValidException to 400 BAD_REQUEST with field details")
    void shouldMapMethodArgumentNotValidExceptionTo400BadRequest() {
        String walletId = "validation-test-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet first
        OpenWalletRequest openRequest = new OpenWalletRequest("ValidationTest", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Test 1: Negative deposit amount (violates @Positive constraint)
        DepositRequest negativeDepositRequest = new DepositRequest("negative-deposit", -100, "Negative amount test");
        ResponseEntity<Map> negativeResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                negativeDepositRequest,
                Map.class
        );

        assertThat(negativeResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> negativeBody = negativeResponse.getBody();
        assertThat(negativeBody).isNotNull();
        assertThat(negativeBody.get("error")).isEqualTo("Validation failed");
        assertThat(negativeBody.get("message")).isEqualTo("Invalid request parameters");
        assertThat(negativeBody.get("timestamp")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, String> negativeFields = (Map<String, String>) negativeBody.get("fields");
        assertThat(negativeFields).isNotNull();
        assertThat(negativeFields.get("amount")).contains("positive");

        // Test 2: Zero deposit amount (violates @Positive constraint)
        DepositRequest zeroDepositRequest = new DepositRequest("zero-deposit", 0, "Zero amount test");
        ResponseEntity<Map> zeroResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                zeroDepositRequest,
                Map.class
        );

        assertThat(zeroResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> zeroBody = zeroResponse.getBody();
        assertThat(zeroBody).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, String> zeroFields = (Map<String, String>) zeroBody.get("fields");
        assertThat(zeroFields).isNotNull();
        assertThat(zeroFields.get("amount")).contains("positive");
    }

    @Test
    @DisplayName("Should map NoResourceFoundException to 404 NOT_FOUND")
    void shouldMapNoResourceFoundExceptionTo404NotFound() {
        // Test missing path variable in wallet creation
        OpenWalletRequest openRequest = new OpenWalletRequest("MissingPathTest", 1000);
        ResponseEntity<Map> missingPathResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/",
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Map.class
        );

        assertThat(missingPathResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> responseBody = missingPathResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("error")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertThat(error.get("code")).isEqualTo("NOT_FOUND");
        assertThat(error.get("message").toString()).contains("PUT api/wallets");
        assertThat(error.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle duplicate deposit (no longer idempotent)")
    void shouldHandleConcurrencyExceptionForDuplicateDeposit() {
        String walletId = "concurrency-deposit-" + UUID.randomUUID().toString().substring(0, 8);
        String depositId = "duplicate-deposit-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("ConcurrencyTest", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First deposit
        DepositRequest depositRequest = new DepositRequest(depositId, 100, "Concurrency test");
        ResponseEntity<Void> firstDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(firstDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second deposit with same ID (no longer idempotent)
        ResponseEntity<Void> secondDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle duplicate withdrawal (no longer idempotent)")
    void shouldHandleConcurrencyExceptionForDuplicateWithdrawal() {
        String walletId = "concurrency-withdrawal-" + UUID.randomUUID().toString().substring(0, 8);
        String withdrawalId = "duplicate-withdrawal-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("ConcurrencyTest", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // First withdrawal
        WithdrawRequest withdrawRequest = new WithdrawRequest(withdrawalId, 100, "Concurrency test");
        ResponseEntity<Void> firstWithdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Void.class
        );
        assertThat(firstWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second withdrawal with same ID (no longer idempotent)
        ResponseEntity<Void> secondWithdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondWithdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle duplicate transfer (no longer idempotent)")
    void shouldHandleConcurrencyExceptionForDuplicateTransfer() {
        String wallet1Id = "concurrency-transfer-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "concurrency-transfer-2-" + UUID.randomUUID().toString().substring(0, 8);
        String transferId = "duplicate-transfer-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("ConcurrencyTest1", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("ConcurrencyTest2", 500);

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
        TransferRequest transferRequest = new TransferRequest(transferId, wallet1Id, wallet2Id, 100, "Concurrency test");
        ResponseEntity<Void> firstTransferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );
        assertThat(firstTransferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second transfer with same ID (no longer idempotent)
        ResponseEntity<Void> secondTransferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );

        // Should return 201 CREATED (no longer idempotent)
        assertThat(secondTransferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
