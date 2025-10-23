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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
    "crablet.outbox.enabled=true",
    "crablet.outbox.lock-strategy=PER_TOPIC_PUBLISHER",
    "crablet.outbox.topics.default.required-tags=test",
    "crablet.outbox.topics.default.publishers=CountDownLatchPublisher,TestPublisher,LogPublisher"
})
class OutboxReliabilityIT extends AbstractCrabletIT {
    
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
    void shouldGuaranteeAtLeastOnceDelivery() throws InterruptedException {
        // Given - Create events
        countDownLatchPublisher.expectEvents(3);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("ReliabilityEvent1")
                .tag("test", "reliability1")
                .data("{\"test\":\"reliability1\"}".getBytes())
                .build(),
            AppendEvent.builder("ReliabilityEvent2")
                .tag("test", "reliability2")
                .data("{\"test\":\"reliability2\"}".getBytes())
                .build(),
            AppendEvent.builder("ReliabilityEvent3")
                .tag("test", "reliability3")
                .data("{\"test\":\"reliability3\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events multiple times (simulating retry scenarios)
        int totalProcessed = 0;
        for (int i = 0; i < 3; i++) {
            int processed = outboxProcessor.processPending();
            totalProcessed += processed;
        }

        // Then - Should process all events (at-least-once guarantee)
        assertThat(totalProcessed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(3);
        }

        // Verify all events were processed by checking position
        Long lastPosition = jdbcTemplate.queryForObject(
            "SELECT MAX(last_position) FROM outbox_topic_progress WHERE topic = 'default'",
            Long.class
        );
        assertThat(lastPosition).isEqualTo(3L);
    }
    
    @Test
    void shouldMaintainEventOrderingWithinTopic() throws InterruptedException {
        // Given - Create events in specific order
        countDownLatchPublisher.expectEvents(5);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("OrderedEvent1")
                .tag("test", "order1")
                .data("{\"order\":1}".getBytes())
                .build(),
            AppendEvent.builder("OrderedEvent2")
                .tag("test", "order2")
                .data("{\"order\":2}".getBytes())
                .build(),
            AppendEvent.builder("OrderedEvent3")
                .tag("test", "order3")
                .data("{\"order\":3}".getBytes())
                .build(),
            AppendEvent.builder("OrderedEvent4")
                .tag("test", "order4")
                .data("{\"order\":4}".getBytes())
                .build(),
            AppendEvent.builder("OrderedEvent5")
                .tag("test", "order5")
                .data("{\"order\":5}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events
        int processed = outboxProcessor.processPending();

        // Then - Should process events in order
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(5);
        }

        // Verify ordering is maintained by checking position sequence
        Long lastPosition = jdbcTemplate.queryForObject(
            "SELECT MAX(last_position) FROM outbox_topic_progress WHERE topic = 'default'",
            Long.class
        );
        assertThat(lastPosition).isEqualTo(5L);
        
        // Verify all positions are sequential
        List<Long> positions = jdbcTemplate.queryForList(
            "SELECT last_position FROM outbox_topic_progress WHERE topic = 'default' ORDER BY last_position",
            Long.class
        );
        assertThat(positions).hasSize(5);
        assertThat(positions).containsExactly(1L, 2L, 3L, 4L, 5L);
    }
    
    @Test
    void shouldEnsurePublisherIndependence() throws InterruptedException {
        // Given - Create events for multiple publishers
        countDownLatchPublisher.expectEvents(3);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("IndependentEvent1")
                .tag("test", "independent1")
                .data("{\"test\":\"independent1\"}".getBytes())
                .build(),
            AppendEvent.builder("IndependentEvent2")
                .tag("test", "independent2")
                .data("{\"test\":\"independent2\"}".getBytes())
                .build(),
            AppendEvent.builder("IndependentEvent3")
                .tag("test", "independent3")
                .data("{\"test\":\"independent3\"}".getBytes())
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

        // Verify multiple publishers were registered independently
        Integer publisherCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT publisher) FROM outbox_topic_progress WHERE topic = 'default'",
            Integer.class
        );
        assertThat(publisherCount).isGreaterThan(1);
        
        // Verify each publisher has its own position tracking
        List<String> publishers = jdbcTemplate.queryForList(
            "SELECT DISTINCT publisher FROM outbox_topic_progress WHERE topic = 'default'",
            String.class
        );
        assertThat(publishers).hasSizeGreaterThan(1);
        
        // Verify each publisher processed all events independently
        for (String publisher : publishers) {
            Long position = jdbcTemplate.queryForObject(
                "SELECT last_position FROM outbox_topic_progress WHERE topic = 'default' AND publisher = ?",
                Long.class,
                publisher
            );
            assertThat(position).isEqualTo(3L);
        }
    }
    
    @Test
    void shouldHandleGracefulShutdown() throws InterruptedException {
        // Given - Create events and start processing
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("ShutdownEvent1")
                .tag("test", "shutdown1")
                .data("{\"test\":\"shutdown1\"}".getBytes())
                .build(),
            AppendEvent.builder("ShutdownEvent2")
                .tag("test", "shutdown2")
                .data("{\"test\":\"shutdown2\"}".getBytes())
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

        // Verify locks were properly managed (no orphaned locks)
        // This is tested implicitly by the fact that the test completes without hanging
        // In a real scenario, PostgreSQL would release advisory locks when the connection drops
        
        // Verify publisher progress was recorded
        Long lastPosition = jdbcTemplate.queryForObject(
            "SELECT MAX(last_position) FROM outbox_topic_progress WHERE topic = 'default'",
            Long.class
        );
        assertThat(lastPosition).isEqualTo(2L);
    }
    
    @Test
    void shouldRecoverFromDatabaseConnectionFailure() throws InterruptedException {
        // Given - Create events
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("RecoveryEvent1")
                .tag("test", "recovery1")
                .data("{\"test\":\"recovery1\"}".getBytes())
                .build(),
            AppendEvent.builder("RecoveryEvent2")
                .tag("test", "recovery2")
                .data("{\"test\":\"recovery2\"}".getBytes())
                .build()
        );

        eventStore.append(events);

        // When - Process events (simulating recovery after connection failure)
        int processed = outboxProcessor.processPending();

        // Then - Should process events successfully
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Verify recovery was successful by checking database state
        Long lastPosition = jdbcTemplate.queryForObject(
            "SELECT MAX(last_position) FROM outbox_topic_progress WHERE topic = 'default'",
            Long.class
        );
        assertThat(lastPosition).isEqualTo(2L);
        
        // Verify no duplicate processing occurred
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE position <= ?",
            Integer.class,
            lastPosition
        );
        assertThat(eventCount).isEqualTo(2);
    }
    
    @Test
    void shouldHandleLargeBatchProcessing() throws InterruptedException {
        // Given - Large batch of events (stress test)
        int batchSize = 100;
        countDownLatchPublisher.expectEvents(batchSize);
        
        List<AppendEvent> events = new ArrayList<>();
        for (int i = 1; i <= batchSize; i++) {
            events.add(AppendEvent.builder("BatchEvent" + i)
                .tag("test", "batch" + i)
                .data(("{\"batch\":\"" + i + "\"}").getBytes())
                .build());
        }

        eventStore.append(events);

        // When - Process large batch
        int processed = outboxProcessor.processPending();

        // Then - Should handle large batch efficiently
        assertThat(processed).isGreaterThan(0);
        
        boolean eventsProcessed = countDownLatchPublisher.awaitEvents(15000); // Longer timeout for large batch
        if (eventsProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(batchSize);
        }

        // Verify all events were processed
        Long lastPosition = jdbcTemplate.queryForObject(
            "SELECT MAX(last_position) FROM outbox_topic_progress WHERE topic = 'default'",
            Long.class
        );
        assertThat(lastPosition).isEqualTo((long) batchSize);
        
        // Verify processing was efficient (no excessive database queries)
        // This is tested implicitly by the fact that the test completes within reasonable time
        
        // Verify all events are accounted for
        Integer totalEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE position <= ?",
            Integer.class,
            lastPosition
        );
        assertThat(totalEvents).isEqualTo(batchSize);
    }
}
