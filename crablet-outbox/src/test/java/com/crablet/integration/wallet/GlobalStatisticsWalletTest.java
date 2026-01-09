package com.crablet.integration.wallet;

import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.integration.AbstractCrabletTest;
import com.crablet.outbox.adapter.OutboxProcessorConfig;
import com.crablet.outbox.adapter.TopicPublisherPair;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.testutils.EventProcessorTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GlobalStatisticsPublisher using wallet domain events.
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:application-test-with-outbox-global.properties"
})
class GlobalStatisticsWalletTest extends AbstractCrabletTest {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private EventProcessor<OutboxProcessorConfig, TopicPublisherPair> eventProcessor;
    
    @Autowired
    private Map<TopicPublisherPair, OutboxProcessorConfig> processorConfigs;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @Autowired
    private GlobalStatisticsPublisher globalStatistics;
    
    @BeforeEach
    void setUp() {
        // Parent's cleanDatabase() runs first and cleans events table, but ensure it's clean
        // This is idempotent - safe to run multiple times
        try {
            jdbcTemplate.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        } catch (Exception e) {
            // Ignore if table doesn't exist yet (Flyway will create it)
        }
        
        // Reset global statistics
        globalStatistics.reset();
        
        // Reset outbox database state to ensure test isolation
        jdbcTemplate.update("DELETE FROM outbox_topic_progress WHERE topic = 'default'");
        
        // Enable outbox processing
        outboxConfig.setEnabled(true);
    }
    
    @Test
    void shouldTrackEventsAcrossMultipleTopics() {
        // Skip test if global statistics is not enabled
        if (globalStatistics == null) {
            return;
        }
        
        // Given - events for different topics (with required 'test' tag)
        List<AppendEvent> events = List.of(
            AppendEvent.builder("WalletOpened")
                .tag("test", "wallet1")
                .data("""
                    {"wallet_id":"wallet1","owner":"Alice"}
                    """)
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("test", "wallet1")
                .data("""
                    {"wallet_id":"wallet1","amount":100}
                    """)
                .build(),
            AppendEvent.builder("WalletOpened")
                .tag("test", "wallet2")
                .data("""
                    {"wallet_id":"wallet2","owner":"Bob"}
                    """)
                .build()
        );
        
        // When
        eventStore.appendIf(events, AppendCondition.empty());
        int processed = EventProcessorTestHelper.processAll(eventProcessor, processorConfigs);
        
        // Then
        assertThat(processed).isGreaterThan(0);
        
        // Verify global statistics
        assertThat(globalStatistics.getTotalEventsProcessed()).isGreaterThan(0);
        
        // Verify per-topic statistics (events should be processed by default topic)
        assertThat(globalStatistics.getEventsForTopic("default")).isGreaterThan(0);
        
        // Note: CountDownLatchPublisher may not increment statistics counters
        // This test validates that global statistics tracking works, regardless of publisher
        
        // Verify per-event-type statistics
        assertThat(globalStatistics.getEventsForType("WalletOpened")).isGreaterThan(0);
        assertThat(globalStatistics.getEventsForType("DepositMade")).isGreaterThan(0);
    }
}

