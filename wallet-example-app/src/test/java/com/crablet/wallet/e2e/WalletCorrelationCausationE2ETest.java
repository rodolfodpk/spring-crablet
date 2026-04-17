package com.crablet.wallet.e2e;

import com.crablet.automations.internal.AutomationProcessorConfig;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.examples.notification.events.WelcomeNotificationSent;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.wallet.TestApplication;
import com.crablet.wallet.api.dto.DepositRequest;
import com.crablet.wallet.api.dto.OpenWalletRequest;
import com.crablet.wallet.api.dto.WithdrawRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static com.crablet.eventstore.EventType.type;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test verifying correlation and causation ID propagation across multiple
 * HTTP commands and an automation chain.
 * <p>
 * Scenario:
 * <pre>
 *   HTTP (corr-1) → OpenWallet   → WalletOpened             (corr-1, causation=null)
 *   Automation                   → WelcomeNotificationSent  (corr-1, causation=WalletOpened.position)
 *   HTTP (corr-2) → Deposit      → DepositMade              (corr-2, causation=null)
 *   HTTP (corr-3) → Withdraw     → WithdrawalMade           (corr-3, causation=null)
 * </pre>
 * Assertions use {@link StoredEvent#correlationId()} and {@link StoredEvent#causationId()}
 * through {@link EventRepository} — not raw SQL — to verify the public API surface.
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
@DisplayName("Correlation and Causation ID E2E Tests")
class WalletCorrelationCausationE2ETest extends AbstractWalletE2ETest {

    private static final String WALLET_ID = "wallet-corr-causation-e2e";
    private static final String AUTOMATION_NAME = "wallet-opened-welcome-notification";

    @Autowired
    @Qualifier("automationsEventProcessor")
    private EventProcessor<AutomationProcessorConfig, String> automationsEventProcessor;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Each HTTP command carries its own correlation ID; automation chain propagates correlation and sets causation")
    void correlationAndCausationAcrossMultipleCommandsAndAutomationChain() {
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE automation_progress CASCADE");

        UUID corr1 = UUID.randomUUID(); // open wallet request
        UUID corr2 = UUID.randomUUID(); // deposit request
        UUID corr3 = UUID.randomUUID(); // withdraw request

        // --- Command 1: open wallet ---
        webTestClient.post().uri("/api/wallets")
            .header("X-Correlation-ID", corr1.toString())
            .bodyValue(new OpenWalletRequest(WALLET_ID, "Alice", 100))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().valueEquals("X-Correlation-ID", corr1.toString());

        // Trigger automation synchronously: WalletOpened → WelcomeNotificationSent
        automationsEventProcessor.process(AUTOMATION_NAME);

        // --- Command 2: deposit ---
        webTestClient.post().uri("/api/wallets/{id}/deposits", WALLET_ID)
            .header("X-Correlation-ID", corr2.toString())
            .bodyValue(new DepositRequest("deposit-corr-1", 50, "Salary"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Correlation-ID", corr2.toString());

        // --- Command 3: withdraw ---
        webTestClient.post().uri("/api/wallets/{id}/withdrawals", WALLET_ID)
            .header("X-Correlation-ID", corr3.toString())
            .bodyValue(new WithdrawRequest("withdrawal-corr-1", 30, "Purchase"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Correlation-ID", corr3.toString());

        // --- Query events via EventRepository (not raw SQL) ---
        StoredEvent walletOpened = querySingle(
            QueryBuilder.builder().event(type(WalletOpened.class), "wallet_id", WALLET_ID).build());
        StoredEvent welcomeNotification = querySingle(
            QueryBuilder.builder().event(type(WelcomeNotificationSent.class), "wallet_id", WALLET_ID).build());
        StoredEvent depositMade = querySingle(
            QueryBuilder.builder().event(type(DepositMade.class), "wallet_id", WALLET_ID).build());
        StoredEvent withdrawalMade = querySingle(
            QueryBuilder.builder().event(type(WithdrawalMade.class), "wallet_id", WALLET_ID).build());

        // --- WalletOpened: carries corr1, no causation (HTTP-originated) ---
        assertThat(walletOpened.correlationId()).isEqualTo(corr1);
        assertThat(walletOpened.causationId()).isNull();

        // --- WelcomeNotificationSent: inherits corr1 from automation chain,
        //     causation = WalletOpened.position (the event that triggered it) ---
        assertThat(welcomeNotification.correlationId()).isEqualTo(corr1);
        assertThat(welcomeNotification.causationId()).isEqualTo(walletOpened.position());

        // --- DepositMade: carries corr2, no causation ---
        assertThat(depositMade.correlationId()).isEqualTo(corr2);
        assertThat(depositMade.causationId()).isNull();

        // --- WithdrawalMade: carries corr3, no causation ---
        assertThat(withdrawalMade.correlationId()).isEqualTo(corr3);
        assertThat(withdrawalMade.causationId()).isNull();

        // --- Each HTTP request produced a distinct correlation ID ---
        assertThat(corr1).isNotEqualTo(corr2).isNotEqualTo(corr3);
    }

    private StoredEvent querySingle(Query query) {
        List<StoredEvent> events = eventRepository.query(query, null);
        assertThat(events).as("expected exactly one event for query %s", query).hasSize(1);
        return events.get(0);
    }
}
