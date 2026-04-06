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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test verifying the automation pipeline:
 * WalletOpened event → WalletOpenedReaction → SendWelcomeNotificationCommand → WelcomeNotificationSent event.
 * <p>
 * Also verifies idempotency: running the automation twice produces exactly one notification.
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
    @DisplayName("Should send welcome notification when wallet is opened")
    void shouldSendWelcomeNotificationWhenWalletIsOpened() {
        // Clean state
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE reaction_progress CASCADE");

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
    @Order(2)
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
    @Order(3)
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
