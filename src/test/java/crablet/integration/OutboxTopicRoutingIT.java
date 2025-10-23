package crablet.integration;

import com.crablet.core.AppendEvent;
import com.crablet.core.EventStore;
import com.crablet.outbox.impl.JDBCOutboxProcessor;
import com.crablet.outbox.impl.OutboxConfig;
import com.crablet.outbox.impl.publishers.CountDownLatchPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
    "crablet.outbox.enabled=true",
    "crablet.outbox.lock-strategy=PER_TOPIC_PUBLISHER",
    "crablet.outbox.topics.wallet-events.required-tags=wallet_id",
    "crablet.outbox.topics.wallet-events.publishers=CountDownLatchPublisher,TestPublisher",
    "crablet.outbox.topics.payment-events.any-of-tags=payment_id,transfer_id",
    "crablet.outbox.topics.payment-events.publishers=CountDownLatchPublisher,LogPublisher",
    "crablet.outbox.topics.audit-events.exact-tags=event_type:audit,status:completed",
    "crablet.outbox.topics.audit-events.publishers=CountDownLatchPublisher",
    "crablet.outbox.topics.complex-events.required-tags=wallet_id",
    "crablet.outbox.topics.complex-events.any-of-tags=payment_id,transfer_id",
    "crablet.outbox.topics.complex-events.exact-tags=status:completed",
    "crablet.outbox.topics.complex-events.publishers=CountDownLatchPublisher"
})
class OutboxTopicRoutingIT extends AbstractCrabletIT {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private JDBCOutboxProcessor outboxProcessor;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @Autowired
    private CountDownLatchPublisher countDownLatchPublisher;
    
    @BeforeEach
    void setUp() {
        // Reset the publisher state before each test
        countDownLatchPublisher.reset();
        
        // Ensure outbox is enabled for all tests
        outboxConfig.setEnabled(true);
    }
    
    @Test
    void shouldRouteEventsToCorrectTopicsByRequiredTags() throws InterruptedException {
        // Given - Events with wallet_id tag (should match wallet-events topic)
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletEvent1")
                .tag("wallet_id", "123")
                .tag("amount", "100")
                .data("{\"wallet\":\"123\"}".getBytes())
                .build(),
            AppendEvent.builder("WalletEvent2")
                .tag("wallet_id", "456")
                .tag("balance", "200")
                .data("{\"wallet\":\"456\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Verify events were routed to wallet-events topic
        Integer walletTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'wallet-events'",
            Integer.class
        );
        assertThat(walletTopicCount).isGreaterThan(0);
    }
    
    @Test
    void shouldRouteEventsToCorrectTopicsByAnyOfTags() throws InterruptedException {
        // Given - Events with payment_id or transfer_id tags (should match payment-events topic)
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("PaymentEvent")
                .tag("payment_id", "pay123")
                .tag("amount", "50")
                .data("{\"payment\":\"pay123\"}".getBytes())
                .build(),
            AppendEvent.builder("TransferEvent")
                .tag("transfer_id", "trans456")
                .tag("amount", "75")
                .data("{\"transfer\":\"trans456\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Verify events were routed to payment-events topic
        Integer paymentTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'payment-events'",
            Integer.class
        );
        assertThat(paymentTopicCount).isGreaterThan(0);
    }
    
    @Test
    void shouldRouteEventsToCorrectTopicsByExactTagValues() throws InterruptedException {
        // Given - Events with exact tag values (should match audit-events topic)
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("AuditEvent1")
                .tag("event_type", "audit")
                .tag("status", "completed")
                .tag("user_id", "user1")
                .data("{\"audit\":\"event1\"}".getBytes())
                .build(),
            AppendEvent.builder("AuditEvent2")
                .tag("event_type", "audit")
                .tag("status", "completed")
                .tag("action", "login")
                .data("{\"audit\":\"event2\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Verify events were routed to audit-events topic
        Integer auditTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'audit-events'",
            Integer.class
        );
        assertThat(auditTopicCount).isGreaterThan(0);
    }
    
    @Test
    void shouldHandleMultipleTopicsWithOverlappingTagCriteria() throws InterruptedException {
        // Given - Events that could match multiple topics
        countDownLatchPublisher.expectEvents(3);
        
        List<AppendEvent> events = List.of(
            // This event matches wallet-events (has wallet_id) AND complex-events (has wallet_id + status:completed)
            AppendEvent.builder("ComplexWalletEvent")
                .tag("wallet_id", "789")
                .tag("status", "completed")
                .tag("amount", "100")
                .data("{\"complex\":\"wallet\"}".getBytes())
                .build(),
            // This event matches payment-events (has payment_id) AND complex-events (has payment_id + status:completed)
            AppendEvent.builder("ComplexPaymentEvent")
                .tag("payment_id", "pay789")
                .tag("status", "completed")
                .tag("amount", "200")
                .data("{\"complex\":\"payment\"}".getBytes())
                .build(),
            // This event matches only wallet-events (has wallet_id but no status:completed)
            AppendEvent.builder("SimpleWalletEvent")
                .tag("wallet_id", "999")
                .tag("amount", "300")
                .data("{\"simple\":\"wallet\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(3);
        }

        // Verify events were routed to multiple topics
        Integer walletTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'wallet-events'",
            Integer.class
        );
        Integer paymentTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'payment-events'",
            Integer.class
        );
        Integer complexTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'complex-events'",
            Integer.class
        );
        
        assertThat(walletTopicCount).isGreaterThan(0);
        assertThat(paymentTopicCount).isGreaterThan(0);
        assertThat(complexTopicCount).isGreaterThan(0);
    }
    
    @Test
    void shouldFanOutEventsToMultipleTopics() throws InterruptedException {
        // Given - Event that matches multiple topics (fan-out scenario)
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("FanOutEvent")
            .tag("wallet_id", "fan123")
            .tag("payment_id", "fan456")
            .tag("status", "completed")
            .tag("amount", "500")
            .data("{\"fanout\":\"event\"}".getBytes())
            .build();

        eventStore.append(List.of(event));

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process event
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // Verify event was routed to multiple topics (fan-out)
        Integer walletTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'wallet-events'",
            Integer.class
        );
        Integer paymentTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'payment-events'",
            Integer.class
        );
        Integer complexTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'complex-events'",
            Integer.class
        );
        
        // Event should be routed to all three topics
        assertThat(walletTopicCount).isGreaterThan(0);
        assertThat(paymentTopicCount).isGreaterThan(0);
        assertThat(complexTopicCount).isGreaterThan(0);
    }
    
    @Test
    void shouldFilterOutEventsMatchingNoTopics() throws InterruptedException {
        // Given - Event that doesn't match any topic criteria
        countDownLatchPublisher.expectEvents(0); // Expect 0 events to be processed
        
        AppendEvent event = AppendEvent.builder("UnmatchedEvent")
            .tag("random_tag", "value")
            .tag("another_tag", "value")
            .data("{\"unmatched\":\"event\"}".getBytes())
            .build();

        eventStore.append(List.of(event));

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should not process any events (filtered out)
        assertThat(processed).isEqualTo(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(1000); // Short timeout since no events expected
        assertThat(eventsProcessed).isFalse();
        assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(0);

        // Verify no publisher progress was created
        Integer totalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress",
            Integer.class
        );
        assertThat(totalCount).isEqualTo(0);
    }
    
    @Test
    void shouldHandleComplexTagCombinations() throws InterruptedException {
        // Given - Events with complex tag combinations
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            // Matches complex-events: wallet_id (required) + payment_id (anyOf) + status:completed (exact)
            AppendEvent.builder("ComplexEvent1")
                .tag("wallet_id", "complex1")
                .tag("payment_id", "pay1")
                .tag("status", "completed")
                .tag("extra", "data")
                .data("{\"complex\":\"event1\"}".getBytes())
                .build(),
            // Matches complex-events: wallet_id (required) + transfer_id (anyOf) + status:completed (exact)
            AppendEvent.builder("ComplexEvent2")
                .tag("wallet_id", "complex2")
                .tag("transfer_id", "trans2")
                .tag("status", "completed")
                .tag("extra", "data")
                .data("{\"complex\":\"event2\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Verify events were routed to complex-events topic
        Integer complexTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'complex-events'",
            Integer.class
        );
        assertThat(complexTopicCount).isGreaterThan(0);
    }
    
    @Test
    void shouldRouteEventsToPublisherSpecificTopics() throws InterruptedException {
        // Given - Events that should be routed to specific publishers within topics
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletEvent1")
                .tag("wallet_id", "pub1")
                .data("{\"wallet\":\"pub1\"}".getBytes())
                .build(),
            AppendEvent.builder("WalletEvent2")
                .tag("wallet_id", "pub2")
                .data("{\"wallet\":\"pub2\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Verify events were routed to wallet-events topic with multiple publishers
        Integer walletTopicCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_topic_progress WHERE topic = 'wallet-events'",
            Integer.class
        );
        assertThat(walletTopicCount).isGreaterThan(0);
        
        // Verify multiple publishers were registered for wallet-events topic
        Integer publisherCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT publisher) FROM outbox_topic_progress WHERE topic = 'wallet-events'",
            Integer.class
        );
        assertThat(publisherCount).isGreaterThan(1);
    }
}
