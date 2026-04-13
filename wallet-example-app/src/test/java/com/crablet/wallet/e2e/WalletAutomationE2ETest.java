package com.crablet.wallet.e2e;

import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.wallet.TestApplication;
import com.crablet.wallet.api.dto.OpenWalletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test verifying the automation pipeline:
 * WalletOpened event → WalletOpenedAutomation → SendWelcomeNotificationCommand → WelcomeNotificationSent event.
 * <p>
 * The example uses the lightweight automation-to-command bridge pattern; the
 * command handler only records the resulting fact. Also verifies idempotency:
 * running the automation twice produces exactly one notification.
 */
@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true",
        "crablet.automations.enabled=true",
        "crablet.automations.polling-interval-ms=500"
    }
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Wallet Automation E2E Tests")
class WalletAutomationE2ETest extends AbstractWalletE2ETest {

    private static final String WALLET_ID = "wallet-automation-e2e-1";
    private static final String AUTOMATION_NAME = "wallet-opened-welcome-notification";

    @Autowired
    @Qualifier("automationsEventProcessor")
    private EventProcessor<AutomationProcessorConfig, String> automationsEventProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    @DisplayName("Should persist correlation and causation metadata across automation chain")
    void shouldPersistCorrelationAndCausationAcrossAutomationChain() {
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE automation_progress CASCADE");

        UUID correlationId = UUID.randomUUID();

        webTestClient
            .post().uri("/api/wallets")
            .header("X-Correlation-ID", correlationId.toString())
            .bodyValue(new OpenWalletRequest(WALLET_ID, "Alice", 100))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().valueEquals("X-Correlation-ID", correlationId.toString());

        automationsEventProcessor.process(AUTOMATION_NAME);

        Map<String, Object> walletOpened = jdbcTemplate.queryForMap(
            """
            SELECT position, correlation_id, causation_id
            FROM events
            WHERE type = 'WalletOpened' AND tags @> ARRAY[?]::text[]
            ORDER BY position
            LIMIT 1
            """,
            "wallet_id=" + WALLET_ID
        );

        Map<String, Object> welcomeNotification = jdbcTemplate.queryForMap(
            """
            SELECT position, correlation_id, causation_id
            FROM events
            WHERE type = 'WelcomeNotificationSent' AND tags @> ARRAY[?]::text[]
            ORDER BY position
            LIMIT 1
            """,
            "wallet_id=" + WALLET_ID
        );

        Number walletOpenedPosition = (Number) walletOpened.get("position");
        Number welcomeCausationId = (Number) welcomeNotification.get("causation_id");

        assertThat(walletOpened.get("correlation_id")).isEqualTo(correlationId);
        assertThat(walletOpened.get("causation_id")).isNull();
        assertThat(welcomeNotification.get("correlation_id")).isEqualTo(correlationId);
        assertThat(welcomeCausationId).isNotNull();
        assertThat(welcomeCausationId.longValue()).isEqualTo(walletOpenedPosition.longValue());
    }

    @Test
    @Order(2)
    @DisplayName("Should send welcome notification when wallet is opened")
    void shouldSendWelcomeNotificationWhenWalletIsOpened() {
        // Clean state
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE automation_progress CASCADE");

        // Open wallet via HTTP
        webTestClient
            .post().uri("/api/wallets")
            .bodyValue(new OpenWalletRequest(WALLET_ID, "Alice", 100))
            .exchange()
            .expectStatus().isCreated();

        // Trigger automation processing synchronously
        automationsEventProcessor.process(AUTOMATION_NAME);

        // Verify WelcomeNotificationSent event was appended
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE type = 'WelcomeNotificationSent'",
            Long.class
        );
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @Order(3)
    @DisplayName("Should not send duplicate welcome notification (idempotency)")
    void shouldNotSendDuplicateWelcomeNotification() {
        // Run automation again on the same events — already-processed position remembered
        automationsEventProcessor.process(AUTOMATION_NAME);

        // Should still be exactly one notification (DCB idempotency on WelcomeNotificationSent)
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE type = 'WelcomeNotificationSent'",
            Long.class
        );
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @Order(4)
    @DisplayName("Should send notifications for multiple wallets independently")
    void shouldSendNotificationForEachWallet() {
        // Open a second wallet
        webTestClient
            .post().uri("/api/wallets")
            .bodyValue(new OpenWalletRequest("wallet-automation-e2e-2", "Bob", 200))
            .exchange()
            .expectStatus().isCreated();

        // Process — automation will pick up the new WalletOpened event
        automationsEventProcessor.process(AUTOMATION_NAME);

        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE type = 'WelcomeNotificationSent'",
            Long.class
        );
        assertThat(count).isEqualTo(2L);
    }
}
