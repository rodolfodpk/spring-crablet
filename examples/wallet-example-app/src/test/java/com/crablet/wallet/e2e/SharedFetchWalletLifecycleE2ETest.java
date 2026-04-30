package com.crablet.wallet.e2e;

import com.crablet.command.web.CommandApiResponse;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.views.internal.ViewProcessorConfig;
import com.crablet.wallet.TestApplication;
import com.crablet.wallet.cleanup.WalletIntegrationTestDbCleanup;
import com.crablet.wallet.cleanup.WalletViewProgressFixtures;
import com.crablet.wallet.api.dto.WalletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * E2E tests for the shared-fetch view path ({@code crablet.views.shared-fetch.enabled=true}).
 *
 * <p>Verifies that the same wallet lifecycle works correctly when all views in the module
 * share a single DB fetch per cycle rather than issuing one query per view. Also checks
 * that the scan-progress tables are populated as the module cursor advances.
 */
@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.main.allow-bean-definition-overriding=true",
                "crablet.views.enabled=true",
                "crablet.views.polling-interval-ms=500",
                "crablet.views.shared-fetch.enabled=true",
                "crablet.views.fetch-batch-size=500"
        }
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Shared-Fetch Wallet Lifecycle E2E Tests")
class SharedFetchWalletLifecycleE2ETest extends AbstractWalletE2ETest {

    private static final String WALLET_ID = "wallet-shared-fetch-1";
    private static final String OWNER = "Jane Shared";

    @Autowired
    @Qualifier("viewsEventProcessor")
    private EventProcessor<ViewProcessorConfig, String> viewsEventProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Trigger one shared-fetch cycle synchronously by calling process() on any view —
     * in shared-fetch mode this runs runSharedCycle() which serves all views at once.
     */
    private void processAllViews() {
        viewsEventProcessor.process("wallet-balance-view");
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Setup: clean database and open wallet")
    void shouldOpenWallet() {
        WalletIntegrationTestDbCleanup.truncateForWalletViewLifecycleE2e(jdbcTemplate);
        WalletViewProgressFixtures.reseedDefaultWalletViews(jdbcTemplate);
        WalletIntegrationTestDbCleanup.truncateSharedFetchScanProgressBestEffort(jdbcTemplate);

        webTestClient.post()
                .uri("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"commandType":"open_wallet","walletId":"%s","owner":"%s","initialBalance":100}
                    """.formatted(WALLET_ID, OWNER))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CommandApiResponse.class)
                .value(r -> assertThat(r.status()).isEqualTo("CREATED"));

        processAllViews();
    }

    // ── View correctness ───────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Balance view reflects wallet open")
    void shouldQueryBalanceAfterOpen() {
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() ->
                webTestClient.get()
                        .uri("/api/wallets/{walletId}", WALLET_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(WalletResponse.class)
                        .value(r -> {
                            assertThat(r.walletId()).isEqualTo(WALLET_ID);
                            assertThat(r.balance()).isEqualTo(100);
                        }));
    }

    @Test
    @Order(3)
    @DisplayName("Deposit is reflected in balance view")
    void shouldDepositAndSeeUpdatedBalance() {
        webTestClient.post()
                .uri("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"commandType":"deposit","depositId":"dep-sf-1","walletId":"%s","amount":50,"description":"top-up"}
                    """.formatted(WALLET_ID))
                .exchange()
                .expectStatus().isCreated();

        processAllViews();

        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() ->
                webTestClient.get()
                        .uri("/api/wallets/{walletId}", WALLET_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(WalletResponse.class)
                        .value(r -> assertThat(r.balance()).isEqualTo(150)));
    }

    @Test
    @Order(4)
    @DisplayName("Withdrawal is reflected in balance view")
    void shouldWithdrawAndSeeUpdatedBalance() {
        webTestClient.post()
                .uri("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"commandType":"withdraw","withdrawalId":"wdr-sf-1","walletId":"%s","amount":30,"description":"purchase"}
                    """.formatted(WALLET_ID))
                .exchange()
                .expectStatus().isCreated();

        processAllViews();

        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() ->
                webTestClient.get()
                        .uri("/api/wallets/{walletId}", WALLET_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(WalletResponse.class)
                        .value(r -> assertThat(r.balance()).isEqualTo(120)));
    }

    @Test
    @Order(5)
    @DisplayName("Transaction history contains deposit and withdrawal")
    void shouldHaveTransactionHistory() {
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() ->
                webTestClient.get()
                        .uri("/api/wallets/{walletId}/transactions", WALLET_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .value(r -> {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> txs = (List<Map<String, Object>>) r.get("transactions");
                            assertThat(txs).isNotNull().hasSizeGreaterThanOrEqualTo(2);
                        }));
    }

    @Test
    @Order(6)
    @DisplayName("Summary view reflects correct totals")
    void shouldHaveCorrectSummary() {
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() ->
                webTestClient.get()
                        .uri("/api/wallets/{walletId}/summary", WALLET_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .value(r -> {
                            assertThat(BigDecimal.valueOf(((Number) r.get("totalDeposits")).doubleValue()))
                                    .isEqualByComparingTo(BigDecimal.valueOf(50));
                            assertThat(BigDecimal.valueOf(((Number) r.get("totalWithdrawals")).doubleValue()))
                                    .isEqualByComparingTo(BigDecimal.valueOf(30));
                            assertThat(BigDecimal.valueOf(((Number) r.get("currentBalance")).doubleValue()))
                                    .isEqualByComparingTo(BigDecimal.valueOf(120));
                        }));
    }

    // ── Scan-progress table verification ──────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Module scan cursor advanced beyond zero in crablet_module_scan_progress")
    void moduleScanCursorShouldHaveAdvanced() {
        Long cursor = jdbcTemplate.queryForObject(
                "SELECT scan_position FROM crablet_module_scan_progress WHERE module_name = 'views'",
                Long.class);

        assertThat(cursor)
                .as("module scan cursor should have advanced after processing events")
                .isNotNull()
                .isGreaterThan(0L);
    }

    @Test
    @Order(8)
    @DisplayName("Per-processor scanned positions recorded in crablet_processor_scan_progress")
    void processorScanPositionsShouldBeRecorded() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT processor_id, scanned_position FROM crablet_processor_scan_progress " +
                "WHERE module_name = 'views' ORDER BY processor_id");

        assertThat(rows)
                .as("each active view should have a scanned_position row")
                .isNotEmpty();

        for (Map<String, Object> row : rows) {
            long scannedPosition = ((Number) row.get("scanned_position")).longValue();
            assertThat(scannedPosition)
                    .as("scanned_position for %s should be > 0", row.get("processor_id"))
                    .isGreaterThan(0L);
        }
    }
}
