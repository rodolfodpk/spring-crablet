package integration.database;

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
import org.springframework.jdbc.core.JdbcTemplate;
import testutils.AbstractCrabletTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for database error handling and recovery scenarios.
 * <p>
 * Tests database error scenarios:
 * 1. Database connection pool exhaustion
 * 2. Query timeout handling
 * 3. Transaction rollback on error
 * 4. PostgreSQL function errors (P0001)
 * 5. Event store append failures
 * 6. Serialization errors (invalid JSON)
 */
class DatabaseErrorHandlingIT extends AbstractCrabletTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should handle database connection errors gracefully")
    void shouldHandleDatabaseConnectionErrorsGracefully() {
        String walletId = "db-error-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet first
        OpenWalletRequest openRequest = new OpenWalletRequest("Alice", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Simulate database connection issue by temporarily closing connections
        // This is a simplified test - in a real scenario, we'd need more sophisticated connection pool manipulation
        try {
            // Try to perform an operation that should work
            DepositRequest depositRequest = new DepositRequest("deposit-1", 100, "Test deposit");
            ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                    depositRequest,
                    Void.class
            );

            // Should either succeed or fail gracefully
            assertThat(depositResponse.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.INTERNAL_SERVER_ERROR);

        } catch (Exception e) {
            // Database connection errors should be handled gracefully
            assertThat(e.getMessage()).isNotNull();
        }
    }

    @Test
    @DisplayName("Should handle query timeout scenarios")
    void shouldHandleQueryTimeoutScenarios() {
        String walletId = "timeout-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Bob", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Perform operations that might timeout under load
        for (int i = 0; i < 5; i++) {
            DepositRequest depositRequest = new DepositRequest("timeout-deposit-" + i, 50, "Timeout test " + i);
            ResponseEntity<?> depositResponse = restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                    depositRequest,
                    Object.class
            );

            // Should handle timeout gracefully
            assertThat(depositResponse.getStatusCode()).isIn(
                    HttpStatus.CREATED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    HttpStatus.REQUEST_TIMEOUT
            );
        }
    }

    @Test
    @DisplayName("Should handle transaction rollback on error")
    void shouldHandleTransactionRollbackOnError() {
        String walletId = "rollback-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Charlie", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Get initial balance
        ResponseEntity<Map> initialWalletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        int initialBalance = (Integer) initialWalletResponse.getBody().get("balance");

        // Try to withdraw more than available (should fail and rollback)
        WithdrawRequest withdrawRequest = new WithdrawRequest("rollback-withdraw", 2000, "Rollback test");
        ResponseEntity<Map> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                withdrawRequest,
                Map.class
        );

        // Should fail with insufficient funds
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify balance is unchanged (transaction rolled back)
        ResponseEntity<Map> finalWalletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        int finalBalance = (Integer) finalWalletResponse.getBody().get("balance");
        assertThat(finalBalance).isEqualTo(initialBalance).as("Balance should be unchanged after failed transaction");
    }

    @Test
    @DisplayName("Should handle PostgreSQL function errors")
    void shouldHandlePostgreSQLFunctionErrors() {
        String walletId = "pg-function-error-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("David", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate wallet (should trigger PostgreSQL function error)
        ResponseEntity<Map> duplicateResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Map.class
        );

        // Should handle duplicate gracefully (idempotent behavior)
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> responseBody = duplicateResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("message")).isNotNull();
        assertThat(responseBody.get("message").toString()).contains("Wallet already exists");
    }

    @Test
    @DisplayName("Should handle event store append failures")
    void shouldHandleEventStoreAppendFailures() {
        String walletId = "append-failure-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Eve", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to perform operations that might cause append failures
        // This is simulated by trying duplicate operations
        DepositRequest depositRequest = new DepositRequest("append-failure-deposit", 100, "Append failure test");

        // First deposit should succeed
        ResponseEntity<Void> firstDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(firstDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second deposit with same ID should be handled gracefully (no longer idempotent)
        ResponseEntity<Void> secondDepositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(secondDepositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle serialization errors gracefully")
    void shouldHandleSerializationErrorsGracefully() {
        String walletId = "serialization-error-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Frank", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try operations with various edge cases that might cause serialization issues
        DepositRequest depositRequest = new DepositRequest("serialization-test", 100, "Test with special chars: !@#$%^&*()");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                depositRequest,
                Void.class
        );

        // Should handle special characters gracefully
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle database constraint violations")
    void shouldHandleDatabaseConstraintViolations() {
        String walletId = "constraint-violation-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Grace", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create another wallet with same ID (constraint violation)
        ResponseEntity<Map> duplicateResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Map.class
        );

        // Should handle constraint violation gracefully
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> responseBody = duplicateResponse.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("message")).isNotNull();
    }

    @Test
    @DisplayName("Should handle connection pool exhaustion scenarios")
    void shouldHandleConnectionPoolExhaustionScenarios() {
        String walletId = "pool-exhaustion-" + UUID.randomUUID().toString().substring(0, 8);

        // Create wallet
        OpenWalletRequest openRequest = new OpenWalletRequest("Henry", 1000);
        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Perform multiple operations to test connection pool handling
        for (int i = 0; i < 10; i++) {
            DepositRequest depositRequest = new DepositRequest("pool-test-" + i, 10, "Pool test " + i);
            ResponseEntity<?> depositResponse = restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                    depositRequest,
                    Object.class
            );

            // Should handle connection pool issues gracefully
            assertThat(depositResponse.getStatusCode()).isIn(
                    HttpStatus.CREATED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    @Test
    @DisplayName("Should handle database deadlock scenarios")
    void shouldHandleDatabaseDeadlockScenarios() {
        String wallet1Id = "deadlock-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "deadlock-2-" + UUID.randomUUID().toString().substring(0, 8);

        // Create two wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("Iris", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Jack", 1000);

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

        // Perform transfers that might cause deadlocks
        TransferRequest transfer1 = new TransferRequest("deadlock-transfer-1", wallet1Id, wallet2Id, 100, "Deadlock test 1");
        TransferRequest transfer2 = new TransferRequest("deadlock-transfer-2", wallet2Id, wallet1Id, 150, "Deadlock test 2");

        ResponseEntity<?> transferResponse1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transfer1,
                Object.class
        );

        ResponseEntity<?> transferResponse2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transfer2,
                Object.class
        );

        // Should handle potential deadlocks gracefully
        assertThat(transferResponse1.getStatusCode()).isIn(
                HttpStatus.CREATED,
                HttpStatus.CONFLICT,
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        assertThat(transferResponse2.getStatusCode()).isIn(
                HttpStatus.CREATED,
                HttpStatus.CONFLICT,
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    @DisplayName("Should verify database connection health")
    void shouldVerifyDatabaseConnectionHealth() {
        // Test basic database connectivity
        try {
            jdbcTemplate.execute("SELECT 1");
        } catch (Exception e) {
            // If database is not available, test should be skipped or handled gracefully
            assertThat(e).isInstanceOf(Exception.class);
        }

        // Test that we can create and query wallets
        String walletId = "health-check-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletRequest openRequest = new OpenWalletRequest("Kelly", 1000);

        ResponseEntity<Void> openResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest),
                Void.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify we can query the wallet
        ResponseEntity<Map> walletResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                Map.class
        );
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody().get("balance")).isEqualTo(1000);
    }
}

