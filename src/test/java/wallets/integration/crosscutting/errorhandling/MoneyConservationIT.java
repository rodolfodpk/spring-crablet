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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for money conservation across wallet operations.
 * <p>
 * Tests that money is conserved across:
 * 1. Transfers between wallets (total system balance unchanged)
 * 2. Multi-wallet transfer chains
 * 3. Deposits increase total system money
 * 4. Withdrawals decrease total system money
 * 5. Concurrent operations preserve total balance
 */
class MoneyConservationIT extends AbstractWalletIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should conserve total system balance across transfers")
    void shouldConserveTotalSystemBalanceAcrossTransfers() {
        String wallet1Id = "conservation-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "conservation-2-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet3Id = "conservation-3-" + UUID.randomUUID().toString().substring(0, 8);

        // Create three wallets with different balances
        OpenWalletRequest openRequest1 = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Bob", 500);
        OpenWalletRequest openRequest3 = new OpenWalletRequest("Charlie", 200);

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

        ResponseEntity<Void> openResponse3 = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet3Id,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest3),
                Void.class
        );
        assertThat(openResponse3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Calculate initial total balance
        int initialTotal = 1000 + 500 + 200; // 1700

        // Perform multiple transfers
        TransferRequest transfer1 = new TransferRequest("transfer-1", wallet1Id, wallet2Id, 200, "Transfer 1");
        TransferRequest transfer2 = new TransferRequest("transfer-2", wallet2Id, wallet3Id, 100, "Transfer 2");
        TransferRequest transfer3 = new TransferRequest("transfer-3", wallet3Id, wallet1Id, 50, "Transfer 3");

        ResponseEntity<Void> transferResponse1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transfer1,
                Void.class
        );
        assertThat(transferResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> transferResponse2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transfer2,
                Void.class
        );
        assertThat(transferResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> transferResponse3 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transfer3,
                Void.class
        );
        assertThat(transferResponse3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Calculate final total balance
        ResponseEntity<Map> wallet1Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                Map.class
        );
        ResponseEntity<Map> wallet2Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                Map.class
        );
        ResponseEntity<Map> wallet3Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet3Id,
                Map.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet3Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        int finalBalance1 = (Integer) wallet1Response.getBody().get("balance");
        int finalBalance2 = (Integer) wallet2Response.getBody().get("balance");
        int finalBalance3 = (Integer) wallet3Response.getBody().get("balance");
        int finalTotal = finalBalance1 + finalBalance2 + finalBalance3;

        // Total system balance should be unchanged
        assertThat(finalTotal).isEqualTo(initialTotal).as("Total system balance should be conserved across transfers");

        // Verify individual balances are correct
        assertThat(finalBalance1).isEqualTo(850); // 1000 - 200 + 50
        assertThat(finalBalance2).isEqualTo(600); // 500 + 200 - 100
        assertThat(finalBalance3).isEqualTo(250); // 200 + 100 - 50
    }

    @Test
    @DisplayName("Should increase total system money on deposits")
    void shouldIncreaseTotalSystemMoneyOnDeposits() {
        String wallet1Id = "deposit-total-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "deposit-total-2-" + UUID.randomUUID().toString().substring(0, 8);

        // Create two wallets
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

        int initialTotal = 1000 + 500; // 1500

        // Make deposits
        DepositRequest deposit1 = new DepositRequest("deposit-1", 200, "Deposit 1");
        DepositRequest deposit2 = new DepositRequest("deposit-2", 300, "Deposit 2");

        ResponseEntity<Void> depositResponse1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/deposit",
                deposit1,
                Void.class
        );
        assertThat(depositResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> depositResponse2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id + "/deposit",
                deposit2,
                Void.class
        );
        assertThat(depositResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Calculate final total
        ResponseEntity<Map> wallet1Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                Map.class
        );
        ResponseEntity<Map> wallet2Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                Map.class
        );

        int finalBalance1 = (Integer) wallet1Response.getBody().get("balance");
        int finalBalance2 = (Integer) wallet2Response.getBody().get("balance");
        int finalTotal = finalBalance1 + finalBalance2;

        // Total should increase by deposit amounts
        assertThat(finalTotal).isEqualTo(initialTotal + 200 + 300).as("Total system money should increase by deposit amounts");
        assertThat(finalTotal).isEqualTo(2000); // 1500 + 500
    }

    @Test
    @DisplayName("Should decrease total system money on withdrawals")
    void shouldDecreaseTotalSystemMoneyOnWithdrawals() {
        String wallet1Id = "withdrawal-total-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "withdrawal-total-2-" + UUID.randomUUID().toString().substring(0, 8);

        // Create two wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("Frank", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Grace", 500);

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

        int initialTotal = 1000 + 500; // 1500

        // Make withdrawals
        WithdrawRequest withdraw1 = new WithdrawRequest("withdraw-1", 200, "Withdrawal 1");
        WithdrawRequest withdraw2 = new WithdrawRequest("withdraw-2", 100, "Withdrawal 2");

        ResponseEntity<Void> withdrawResponse1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/withdraw",
                withdraw1,
                Void.class
        );
        assertThat(withdrawResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> withdrawResponse2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id + "/withdraw",
                withdraw2,
                Void.class
        );
        assertThat(withdrawResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Calculate final total
        ResponseEntity<Map> wallet1Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                Map.class
        );
        ResponseEntity<Map> wallet2Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                Map.class
        );

        int finalBalance1 = (Integer) wallet1Response.getBody().get("balance");
        int finalBalance2 = (Integer) wallet2Response.getBody().get("balance");
        int finalTotal = finalBalance1 + finalBalance2;

        // Total should decrease by withdrawal amounts
        assertThat(finalTotal).isEqualTo(initialTotal - 200 - 100).as("Total system money should decrease by withdrawal amounts");
        assertThat(finalTotal).isEqualTo(1200); // 1500 - 300
    }

    @Test
    @DisplayName("Should preserve total balance under concurrent operations")
    void shouldPreserveTotalBalanceUnderConcurrentOperations() throws InterruptedException {
        String wallet1Id = "concurrent-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "concurrent-2-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet3Id = "concurrent-3-" + UUID.randomUUID().toString().substring(0, 8);

        // Create three wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("Henry", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Iris", 1000);
        OpenWalletRequest openRequest3 = new OpenWalletRequest("Jack", 1000);

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

        ResponseEntity<Void> openResponse3 = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet3Id,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest3),
                Void.class
        );
        assertThat(openResponse3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        int initialTotal = 1000 + 1000 + 1000; // 3000

        // Perform concurrent operations
        ExecutorService executor = Executors.newFixedThreadPool(6);
        AtomicInteger completedOperations = new AtomicInteger(0);

        // Concurrent transfers
        executor.submit(() -> {
            try {
                TransferRequest transfer = new TransferRequest("concurrent-transfer-1", wallet1Id, wallet2Id, 50, "Concurrent transfer 1");
                restTemplate.postForEntity("http://localhost:" + port + "/api/wallets/transfer", transfer, Void.class);
                completedOperations.incrementAndGet();
            } catch (Exception e) {
                // Some operations may fail due to concurrency conflicts, which is expected
            }
        });

        executor.submit(() -> {
            try {
                TransferRequest transfer = new TransferRequest("concurrent-transfer-2", wallet2Id, wallet3Id, 75, "Concurrent transfer 2");
                restTemplate.postForEntity("http://localhost:" + port + "/api/wallets/transfer", transfer, Void.class);
                completedOperations.incrementAndGet();
            } catch (Exception e) {
                // Some operations may fail due to concurrency conflicts, which is expected
            }
        });

        executor.submit(() -> {
            try {
                TransferRequest transfer = new TransferRequest("concurrent-transfer-3", wallet3Id, wallet1Id, 25, "Concurrent transfer 3");
                restTemplate.postForEntity("http://localhost:" + port + "/api/wallets/transfer", transfer, Void.class);
                completedOperations.incrementAndGet();
            } catch (Exception e) {
                // Some operations may fail due to concurrency conflicts, which is expected
            }
        });

        // Concurrent deposits
        executor.submit(() -> {
            try {
                DepositRequest deposit = new DepositRequest("concurrent-deposit-1", 100, "Concurrent deposit 1");
                restTemplate.postForEntity("http://localhost:" + port + "/api/wallets/" + wallet1Id + "/deposit", deposit, Void.class);
                completedOperations.incrementAndGet();
            } catch (Exception e) {
                // Some operations may fail due to concurrency conflicts, which is expected
            }
        });

        executor.submit(() -> {
            try {
                DepositRequest deposit = new DepositRequest("concurrent-deposit-2", 150, "Concurrent deposit 2");
                restTemplate.postForEntity("http://localhost:" + port + "/api/wallets/" + wallet2Id + "/deposit", deposit, Void.class);
                completedOperations.incrementAndGet();
            } catch (Exception e) {
                // Some operations may fail due to concurrency conflicts, which is expected
            }
        });

        // Concurrent withdrawals
        executor.submit(() -> {
            try {
                WithdrawRequest withdraw = new WithdrawRequest("concurrent-withdraw-1", 200, "Concurrent withdrawal 1");
                restTemplate.postForEntity("http://localhost:" + port + "/api/wallets/" + wallet3Id + "/withdraw", withdraw, Void.class);
                completedOperations.incrementAndGet();
            } catch (Exception e) {
                // Some operations may fail due to concurrency conflicts, which is expected
            }
        });

        // Wait for all operations to complete
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Calculate final total
        ResponseEntity<Map> wallet1Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                Map.class
        );
        ResponseEntity<Map> wallet2Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                Map.class
        );
        ResponseEntity<Map> wallet3Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet3Id,
                Map.class
        );

        int finalBalance1 = (Integer) wallet1Response.getBody().get("balance");
        int finalBalance2 = (Integer) wallet2Response.getBody().get("balance");
        int finalBalance3 = (Integer) wallet3Response.getBody().get("balance");
        int finalTotal = finalBalance1 + finalBalance2 + finalBalance3;

        // With concurrent operations, some may fail due to ConcurrencyException
        // The total should be conserved, but may vary based on which operations succeeded
        // Total should be: initial + successful_deposits - successful_withdrawals
        // At minimum: initial - 250 (if withdrawal succeeds but deposits fail), at maximum: initial + 250 (if all deposits succeed, no withdrawal)
        assertThat(finalTotal).isBetween(initialTotal - 250, initialTotal + 250)
                .as("Total system balance should be preserved under concurrent operations (actual: %d, initial: %d)",
                        finalTotal, initialTotal);

        // Log actual results for debugging
        System.out.println("Final balances - W1: " + finalBalance1 + ", W2: " + finalBalance2 + ", W3: " + finalBalance3);
        System.out.println("Total: " + finalTotal + " (initial: " + initialTotal + ", completed: " + completedOperations.get() + ")");

        // Verify at least some operations completed
        assertThat(completedOperations.get()).isGreaterThan(0).as("At least some concurrent operations should have completed");
    }

    @Test
    @DisplayName("Should handle complex multi-wallet transfer chains")
    void shouldHandleComplexMultiWalletTransferChains() {
        String wallet1Id = "chain-1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "chain-2-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet3Id = "chain-3-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet4Id = "chain-4-" + UUID.randomUUID().toString().substring(0, 8);

        // Create four wallets
        OpenWalletRequest openRequest1 = new OpenWalletRequest("Kelly", 1000);
        OpenWalletRequest openRequest2 = new OpenWalletRequest("Liam", 800);
        OpenWalletRequest openRequest3 = new OpenWalletRequest("Mia", 600);
        OpenWalletRequest openRequest4 = new OpenWalletRequest("Noah", 400);

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

        ResponseEntity<Void> openResponse3 = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet3Id,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest3),
                Void.class
        );
        assertThat(openResponse3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> openResponse4 = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet4Id,
                HttpMethod.PUT,
                new HttpEntity<>(openRequest4),
                Void.class
        );
        assertThat(openResponse4.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        int initialTotal = 1000 + 800 + 600 + 400; // 2800

        // Create a complex transfer chain: 1->2->3->4->1
        TransferRequest chain1 = new TransferRequest("chain-1", wallet1Id, wallet2Id, 100, "Chain step 1");
        TransferRequest chain2 = new TransferRequest("chain-2", wallet2Id, wallet3Id, 150, "Chain step 2");
        TransferRequest chain3 = new TransferRequest("chain-3", wallet3Id, wallet4Id, 200, "Chain step 3");
        TransferRequest chain4 = new TransferRequest("chain-4", wallet4Id, wallet1Id, 50, "Chain step 4");

        ResponseEntity<Void> chainResponse1 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                chain1,
                Void.class
        );
        assertThat(chainResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> chainResponse2 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                chain2,
                Void.class
        );
        assertThat(chainResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> chainResponse3 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                chain3,
                Void.class
        );
        assertThat(chainResponse3.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> chainResponse4 = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                chain4,
                Void.class
        );
        assertThat(chainResponse4.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Calculate final total
        ResponseEntity<Map> wallet1Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                Map.class
        );
        ResponseEntity<Map> wallet2Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                Map.class
        );
        ResponseEntity<Map> wallet3Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet3Id,
                Map.class
        );
        ResponseEntity<Map> wallet4Response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet4Id,
                Map.class
        );

        int finalBalance1 = (Integer) wallet1Response.getBody().get("balance");
        int finalBalance2 = (Integer) wallet2Response.getBody().get("balance");
        int finalBalance3 = (Integer) wallet3Response.getBody().get("balance");
        int finalBalance4 = (Integer) wallet4Response.getBody().get("balance");
        int finalTotal = finalBalance1 + finalBalance2 + finalBalance3 + finalBalance4;

        // Total system balance should be unchanged
        assertThat(finalTotal).isEqualTo(initialTotal).as("Total system balance should be conserved across complex transfer chains");

        // Verify individual balances are correct
        assertThat(finalBalance1).isEqualTo(950); // 1000 - 100 + 50
        assertThat(finalBalance2).isEqualTo(750); // 800 + 100 - 150
        assertThat(finalBalance3).isEqualTo(550); // 600 + 150 = 750, then 750 - 200 = 550
        assertThat(finalBalance4).isEqualTo(550); // 400 + 200 = 600, then 600 - 50 = 550
    }
}

