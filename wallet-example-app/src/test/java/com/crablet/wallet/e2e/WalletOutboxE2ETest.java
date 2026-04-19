package com.crablet.wallet.e2e;

import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.internal.OutboxProcessorConfig;
import com.crablet.wallet.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test verifying the outbox feature: events appended to the event store are
 * forwarded to the registered {@link OutboxPublisher} when the outbox processor runs.
 * <p>
 * Scenario:
 * <pre>
 *   HTTP → OpenWallet → WalletOpened (appended to event store)
 *   outboxEventProcessor.process("wallet-events/LogPublisher")
 *   → CapturingOutboxPublisher.publishBatch([WalletOpened, ...])
 * </pre>
 * The {@link CapturingOutboxPublisher} overrides the production {@link com.crablet.outbox.publishers.LogPublisher}
 * so that test assertions can verify which events were forwarded.
 */
@SpringBootTest(
    classes = {TestApplication.class, WalletOutboxE2ETest.OutboxTestConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true",
        "crablet.outbox.enabled=true",
        "crablet.outbox.polling-interval-ms=500",
        "crablet.outbox.topics.topics.wallet-events.publishers=LogPublisher"
    }
)
@DisplayName("Outbox E2E Tests")
class WalletOutboxE2ETest extends AbstractWalletE2ETest {

    private static final String WALLET_ID = "wallet-outbox-e2e";
    private static final TopicPublisherPair WALLET_EVENTS_PUBLISHER =
            new TopicPublisherPair("wallet-events", "LogPublisher");

    @Autowired
    @Qualifier("outboxEventProcessor")
    private EventProcessor<OutboxProcessorConfig, TopicPublisherPair> outboxEventProcessor;

    @Autowired
    private CapturingOutboxPublisher capturingPublisher;

    @Test
    @DisplayName("Events appended by a command are forwarded to the outbox publisher")
    void walletEventsAreForwardedToOutboxPublisher() {
        jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE commands CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
        capturingPublisher.clear();

        webTestClient.post().uri("/api/commands")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"commandType":"open_wallet","walletId":"%s","owner":"Alice","initialBalance":100}
                """.formatted(WALLET_ID))
            .exchange()
            .expectStatus().isCreated();

        outboxEventProcessor.process(WALLET_EVENTS_PUBLISHER);

        List<StoredEvent> published = capturingPublisher.received();
        assertThat(published).isNotEmpty();
        assertThat(published).anyMatch(e -> "WalletOpened".equals(e.type()));
    }

    // --- Test infrastructure ---

    /**
     * Overrides the production LogPublisher with a recording implementation
     * so tests can assert on which events were forwarded.
     * The bean name must match "LogPublisher" — the name the topic config references.
     */
    @TestConfiguration
    static class OutboxTestConfig {
        @Bean
        public CapturingOutboxPublisher logPublisher() {
            return new CapturingOutboxPublisher();
        }
    }

    static class CapturingOutboxPublisher implements OutboxPublisher {
        private final List<StoredEvent> received = new CopyOnWriteArrayList<>();

        @Override
        public String getName() { return "LogPublisher"; }

        @Override
        public void publishBatch(List<StoredEvent> events) { received.addAll(events); }

        @Override
        public boolean isHealthy() { return true; }

        public List<StoredEvent> received() { return List.copyOf(received); }

        public void clear() { received.clear(); }
    }
}
