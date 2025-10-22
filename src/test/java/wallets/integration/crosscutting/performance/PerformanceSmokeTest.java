package wallets.integration.crosscutting.performance;

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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance smoke tests to catch major regressions.
 * <p>
 * These are NOT comprehensive performance tests - use k6 for that.
 * These tests just verify operations complete within reasonable time.
 * <p>
 * Thresholds are generous to avoid flakiness - just catch major slowdowns.
 */
@DisplayName("Performance Smoke Tests")
class PerformanceSmokeTest extends AbstractWalletIntegrationTest {

    private static final int SMOKE_TEST_TIMEOUT_MS = 500; // Generous threshold
    @Autowired
    private TestRestTemplate restTemplate;
    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Wallet creation should complete quickly")
    void walletCreationShouldBeFast() {
        String walletId = "smoke-wallet-" + UUID.randomUUID();
        OpenWalletRequest request = new OpenWalletRequest("Smoke Test User", 1000);

        long start = System.nanoTime();
        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(request),
                Void.class
        );
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        assertThat(duration).isLessThan(SMOKE_TEST_TIMEOUT_MS)
                .as("Wallet creation took %dms (threshold: %dms)", duration, SMOKE_TEST_TIMEOUT_MS);
    }

    @Test
    @DisplayName("Deposit should complete quickly")
    void depositShouldBeFast() {
        // Setup: create wallet
        String walletId = "smoke-deposit-" + UUID.randomUUID();
        OpenWalletRequest walletRequest = new OpenWalletRequest("Deposit Test", 1000);
        restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(walletRequest),
                Void.class
        );

        // Test: deposit
        DepositRequest request = new DepositRequest("smoke-deposit-1", 100, "Test");

        long start = System.nanoTime();
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/deposit",
                request,
                Void.class
        );
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        assertThat(duration).isLessThan(SMOKE_TEST_TIMEOUT_MS)
                .as("Deposit took %dms (threshold: %dms)", duration, SMOKE_TEST_TIMEOUT_MS);
    }

    @Test
    @DisplayName("Withdrawal should complete quickly")
    void withdrawalShouldBeFast() {
        // Setup: create wallet
        String walletId = "smoke-withdraw-" + UUID.randomUUID();
        OpenWalletRequest walletRequest = new OpenWalletRequest("Withdraw Test", 1000);
        restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(walletRequest),
                Void.class
        );

        // Test: withdrawal
        WithdrawRequest request = new WithdrawRequest("smoke-withdraw-1", 100, "Test");

        long start = System.nanoTime();
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw",
                request,
                Void.class
        );
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        assertThat(duration).isLessThan(SMOKE_TEST_TIMEOUT_MS)
                .as("Withdrawal took %dms (threshold: %dms)", duration, SMOKE_TEST_TIMEOUT_MS);
    }

    @Test
    @DisplayName("Transfer should complete quickly")
    void transferShouldBeFast() {
        // Setup: create two wallets
        String wallet1 = "smoke-transfer-1-" + UUID.randomUUID();
        String wallet2 = "smoke-transfer-2-" + UUID.randomUUID();

        restTemplate.exchange("http://localhost:" + port + "/api/wallets/" + wallet1,
                HttpMethod.PUT, new HttpEntity<>(new OpenWalletRequest("User1", 1000)), Void.class);
        restTemplate.exchange("http://localhost:" + port + "/api/wallets/" + wallet2,
                HttpMethod.PUT, new HttpEntity<>(new OpenWalletRequest("User2", 1000)), Void.class);

        // Test: transfer
        TransferRequest request = new TransferRequest("smoke-transfer-1", wallet1, wallet2, 100, "Test");

        long start = System.nanoTime();
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                request,
                Void.class
        );
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        assertThat(duration).isLessThan(SMOKE_TEST_TIMEOUT_MS)
                .as("Transfer took %dms (threshold: %dms)", duration, SMOKE_TEST_TIMEOUT_MS);
    }

    @Test
    @DisplayName("Wallet query should complete quickly")
    void walletQueryShouldBeFast() {
        // Setup: create wallet
        String walletId = "smoke-query-" + UUID.randomUUID();
        restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(new OpenWalletRequest("Query Test", 1000)),
                Void.class
        );

        // Test: query
        long start = System.nanoTime();
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + walletId,
                String.class
        );
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duration).isLessThan(SMOKE_TEST_TIMEOUT_MS)
                .as("Wallet query took %dms (threshold: %dms)", duration, SMOKE_TEST_TIMEOUT_MS);
    }
}

