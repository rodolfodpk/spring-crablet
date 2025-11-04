package com.crablet.integration;

import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.processor.OutboxProcessorImpl;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for lock acquisition integration tests.
 * Contains all test logic that will be inherited by concrete test classes
 * for each lock strategy (GLOBAL and PER_TOPIC_PUBLISHER).
 */
abstract class AbstractOutboxLockAcquisitionTest extends AbstractCrabletTest {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OutboxProcessorImpl outboxProcessor;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @BeforeEach
    void setUp() {
        outboxConfig.setEnabled(true);
    }
    
    @Test
    void shouldAcquireLocksAtStartup() {
        // Note: With the new implementation, locks are acquired at startup via @PostConstruct
        // But pairs are only created in database when first accessed (auto-registration)
        
        // Create some events to trigger processing
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent1")
                .tag("test", "value1")
                .data("{\"test\":\"data1\"}".getBytes())
                .build()
        );
        eventStore.append(events);
        
        // Trigger initial processing to auto-register publishers and acquire locks
        outboxProcessor.processPending();
        
        // Then - Verify locks are held by checking database
        Integer lockCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) 
            FROM outbox_topic_progress 
            WHERE leader_instance IS NOT NULL 
              AND leader_heartbeat IS NOT NULL
            """,
            Integer.class
        );
        
        // Should have at least 1 publisher registered
        assertThat(lockCount).isGreaterThanOrEqualTo(1);
    }
    
    @Test
    void shouldProcessEventsWhenLocksAreAcquired() throws InterruptedException {
        // Given - Events to process
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent1")
                .tag("test", "value1")
                .data("{\"test\":\"data1\"}".getBytes())
                .build(),
            AppendEvent.builder("TestEvent2")
                .tag("test", "value2")
                .data("{\"test\":\"data2\"}".getBytes())
                .build()
        );
        
        eventStore.append(events);
        
        // When - Process events (locks already acquired at startup)
        int processed = outboxProcessor.processPending();
        
        // Then - Should process events
        assertThat(processed).isGreaterThan(0);
        
        // Verify events were processed by checking positions were updated
        Integer positionsUpdated = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) 
            FROM outbox_topic_progress 
            WHERE last_position > 0
            """,
            Integer.class
        );
        assertThat(positionsUpdated).isGreaterThan(0);
    }
    
    @Test
    void shouldMaintainLockOwnershipAcrossMultiplePolls() throws InterruptedException {
        // Given - Events to process
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent1")
                .tag("test", "value1")
                .data("{\"test\":\"data1\"}".getBytes())
                .build(),
            AppendEvent.builder("TestEvent2")
                .tag("test", "value2")
                .data("{\"test\":\"data2\"}".getBytes())
                .build()
        );
        
        eventStore.append(events);
        
        // When - Process events multiple times
        int processed1 = outboxProcessor.processPending();
        int processed2 = outboxProcessor.processPending();
        
        // Then - Should process events in first poll, none in second (already processed)
        assertThat(processed1).isGreaterThan(0);
        assertThat(processed2).isEqualTo(0);
        
        // Verify all events were processed in first poll
        Integer positionsUpdated = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) 
            FROM outbox_topic_progress 
            WHERE last_position >= 2
            """,
            Integer.class
        );
        assertThat(positionsUpdated).isGreaterThan(0);
    }
}

