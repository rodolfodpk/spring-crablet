package com.crablet.wallet.e2e;

import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.views.adapter.ViewProcessorConfig;
import com.crablet.wallet.api.dto.DepositRequest;
import com.crablet.wallet.api.dto.OpenWalletRequest;
import com.crablet.wallet.api.dto.WalletResponse;
import com.crablet.wallet.api.dto.WithdrawRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * E2E tests for complete wallet lifecycle.
 * <p>
 * Scenario: Open wallet → Deposit → Withdraw → Query views
 * <p>
 * Tests are executed sequentially using @Order annotations to build up state.
 */
@SpringBootTest(
    classes = com.crablet.wallet.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true",
        "crablet.views.enabled=true",
        "crablet.views.polling-interval-ms=500"  // Faster polling for tests
    }
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Wallet Lifecycle E2E Tests")
class WalletLifecycleE2ETest extends AbstractWalletE2ETest {
    
    private static final String WALLET_ID = "wallet-lifecycle-1";
    private static final String OWNER = "John Doe";
    
    @Autowired
    @Qualifier("viewsEventProcessor")
    private EventProcessor<ViewProcessorConfig, String> viewsEventProcessor;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    @Qualifier("viewProcessorConfigs")
    private Map<String, ViewProcessorConfig> processorConfigs;
    
    @Autowired
    @Qualifier("viewSubscriptions")
    private Map<String, com.crablet.views.config.ViewSubscriptionConfig> subscriptions;
    
    /**
     * Process all views to ensure they're up to date.
     * This ensures views are processed synchronously in tests.
     * Note: Processor IDs are the view names from ViewSubscriptionConfig.getViewName().
     */
    private void processAllViews() {
        // Use view names as processor IDs (now that processor config map uses view names as keys)
        int balanceProcessed = viewsEventProcessor.process("wallet-balance-view");
        int transactionProcessed = viewsEventProcessor.process("wallet-transaction-view");
        int summaryProcessed = viewsEventProcessor.process("wallet-summary-view");
        
        // Debug: Check if views were populated
        Long balanceViewCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wallet_balance_view", Long.class);
        List<Map<String, Object>> balanceRows = jdbcTemplate.queryForList("SELECT wallet_id, balance FROM wallet_balance_view");
        System.out.println("DEBUG: Processed views - balance: " + balanceProcessed + 
                          " (view rows: " + balanceViewCount + "), transaction: " + transactionProcessed + 
                          ", summary: " + summaryProcessed);
        System.out.println("DEBUG: Balance view rows: " + balanceRows);
    }
    
    @Test
    @Order(1)
    @DisplayName("Should open a new wallet successfully")
    void shouldOpenWallet() {
        // Clean database before first test
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE view_progress CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE wallet_balance_view CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE wallet_transaction_view CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE wallet_summary_view CASCADE");
        
        // Given
        OpenWalletRequest request = new OpenWalletRequest(
            WALLET_ID,
            OWNER,
            100
        );
        
        // When & Then
        webTestClient
            .post()
            .uri("/api/wallets")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(WalletResponse.class)
            .value(response -> {
                assertThat(response.walletId()).isEqualTo(WALLET_ID);
                assertThat(response.owner()).isEqualTo(OWNER);
                assertThat(response.balance()).isEqualTo(100);
            });
        
        // Process views to ensure they're updated
        processAllViews();
    }
    
    @Test
    @Order(2)
    @DisplayName("Should query wallet after opening")
    void shouldQueryWalletAfterOpening() {
        // Given - Wallet was created in previous test
        
        // When & Then - Views should already be processed, but use Awaitility as fallback
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            webTestClient
                .get()
                .uri("/api/wallets/{walletId}", WALLET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(WalletResponse.class)
                .value(response -> {
                    assertThat(response.walletId()).isEqualTo(WALLET_ID);
                    assertThat(response.owner()).isEqualTo(OWNER);
                    assertThat(response.balance()).isEqualTo(100);
                });
        });
    }
    
    @Test
    @Order(3)
    @DisplayName("Should deposit money into wallet")
    void shouldDepositMoney() {
        // Given - Wallet exists with balance=100
        
        DepositRequest request = new DepositRequest(
            "deposit-lifecycle-1",
            50,
            "Initial deposit"
        );
        
        // When & Then
        webTestClient
            .post()
            .uri("/api/wallets/{walletId}/deposits", WALLET_ID)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(WalletResponse.class)
            .value(response -> {
                assertThat(response.walletId()).isEqualTo(WALLET_ID);
            });
        
        // Process views to ensure they're updated
        processAllViews();
    }
    
    @Test
    @Order(4)
    @DisplayName("Should query wallet after deposit")
    void shouldQueryWalletAfterDeposit() {
        // Given - Deposit of 50 was made to wallet
        
        // When & Then - Views should already be processed, but use Awaitility as fallback
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            webTestClient
                .get()
                .uri("/api/wallets/{walletId}", WALLET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(WalletResponse.class)
                .value(response -> {
                    assertThat(response.walletId()).isEqualTo(WALLET_ID);
                    assertThat(response.balance()).isEqualTo(150);
                });
        });
    }
    
    @Test
    @Order(5)
    @DisplayName("Should withdraw money from wallet")
    void shouldWithdrawMoney() {
        // Given - Wallet has balance=150
        
        WithdrawRequest request = new WithdrawRequest(
            "withdrawal-lifecycle-1",
            30,
            "Purchase"
        );
        
        // When & Then
        webTestClient
            .post()
            .uri("/api/wallets/{walletId}/withdrawals", WALLET_ID)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(WalletResponse.class)
            .value(response -> {
                assertThat(response.walletId()).isEqualTo(WALLET_ID);
            });
        
        // Process views to ensure they're updated
        processAllViews();
    }
    
    @Test
    @Order(6)
    @DisplayName("Should query wallet after withdrawal")
    void shouldQueryWalletAfterWithdrawal() {
        // Given - Withdrawal of 30 was made
        
        // When & Then - Views should already be processed, but use Awaitility as fallback
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            webTestClient
                .get()
                .uri("/api/wallets/{walletId}", WALLET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(WalletResponse.class)
                .value(response -> {
                    assertThat(response.walletId()).isEqualTo(WALLET_ID);
                    assertThat(response.balance()).isEqualTo(120);
                });
        });
    }
    
    @Test
    @Order(7)
    @DisplayName("Should get transaction history")
    void shouldGetTransactionHistory() {
        // Given - Wallet has 2 transactions (deposit, withdraw) - WalletOpened is not in transaction view
        
        // When & Then - Views should already be processed, but use Awaitility as fallback
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            webTestClient
                .get()
                .uri("/api/wallets/{walletId}/transactions", WALLET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(response -> {
                    assertThat(response.get("walletId")).isEqualTo(WALLET_ID);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> transactions = 
                        (List<Map<String, Object>>) response.get("transactions");
                    assertThat(transactions).isNotNull();
                    assertThat(transactions.size()).isGreaterThanOrEqualTo(2);
                });
        });
    }
    
    @Test
    @Order(8)
    @DisplayName("Should get wallet summary")
    void shouldGetWalletSummary() {
        // Given - Wallet has completed operations
        
        // When & Then - Views should already be processed, but use Awaitility as fallback
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            webTestClient
                .get()
                .uri("/api/wallets/{walletId}/summary", WALLET_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(response -> {
                    assertThat(response.get("walletId")).isEqualTo(WALLET_ID);
                    // API returns Double (from JSON serialization), convert to BigDecimal for comparison
                    Object totalDeposits = response.get("totalDeposits");
                    Object totalWithdrawals = response.get("totalWithdrawals");
                    Object currentBalance = response.get("currentBalance");
                    
                    assertThat(BigDecimal.valueOf(((Number) totalDeposits).doubleValue()))
                        .isEqualByComparingTo(BigDecimal.valueOf(50));
                    assertThat(BigDecimal.valueOf(((Number) totalWithdrawals).doubleValue()))
                        .isEqualByComparingTo(BigDecimal.valueOf(30));
                    assertThat(BigDecimal.valueOf(((Number) currentBalance).doubleValue()))
                        .isEqualByComparingTo(BigDecimal.valueOf(120));
                });
        });
    }
}

