package com.crablet.integration;

import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.management.OutboxManagementService;
import com.crablet.outbox.processor.OutboxProcessorImpl;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import com.crablet.integration.AbstractCrabletTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for OutboxManagementService integration tests.
 * Contains all test logic that will be inherited by concrete test classes
 * for each lock strategy (GLOBAL and PER_TOPIC_PUBLISHER).
 */
abstract class AbstractOutboxManagementServiceTest extends AbstractCrabletTest {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OutboxProcessorImpl outboxProcessor;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @Autowired
    private OutboxManagementService outboxManagementService;
    
    @Test
    void shouldPauseAndResumePublisher() {
        // Enable outbox and create some events
        outboxConfig.setEnabled(true);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test_id", "pause-test")
            .data("{\"test\":\"pause\"}".getBytes())
            .build();
        
        eventStore.append(List.of(event));
        
        // Process to register publisher
        outboxProcessor.processPending();
        
        // Verify publisher exists
        assertThat(outboxManagementService.publisherExists("CountDownLatchPublisher")).isTrue();
        
        // Pause publisher
        boolean paused = outboxManagementService.pausePublisher("CountDownLatchPublisher");
        assertThat(paused).isTrue();
        
        // Verify status is PAUSED
        OutboxManagementService.PublisherStatus status = outboxManagementService.getPublisherStatus("CountDownLatchPublisher");
        assertThat(status).isNotNull();
        assertThat(status.isPaused()).isTrue();
        assertThat(status.status()).isEqualTo("PAUSED");
        
        // Resume publisher
        boolean resumed = outboxManagementService.resumePublisher("CountDownLatchPublisher");
        assertThat(resumed).isTrue();
        
        // Verify status is ACTIVE
        status = outboxManagementService.getPublisherStatus("CountDownLatchPublisher");
        assertThat(status).isNotNull();
        assertThat(status.isActive()).isTrue();
        assertThat(status.status()).isEqualTo("ACTIVE");
    }
    
    @Test
    void shouldGetAllPublisherStatus() {
        // Enable outbox and create some events
        outboxConfig.setEnabled(true);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test_id", "status-test")
            .data("{\"test\":\"status\"}".getBytes())
            .build();
        
        eventStore.append(List.of(event));
        
        // Process to register publishers
        outboxProcessor.processPending();
        
        // Get all publisher statuses
        List<OutboxManagementService.PublisherStatus> statuses = outboxManagementService.getAllPublisherStatus();
        
        assertThat(statuses).isNotEmpty();
        assertThat(statuses).hasSizeGreaterThanOrEqualTo(1);
        
        // Verify CountDownLatchPublisher is in the list
        boolean testPublisherFound = statuses.stream()
            .anyMatch(s -> "CountDownLatchPublisher".equals(s.publisherName()));
        assertThat(testPublisherFound).isTrue();
    }
    
    @Test
    void shouldHandleNonExistentPublisher() {
        // Try to pause non-existent publisher
        boolean paused = outboxManagementService.pausePublisher("NonExistentPublisher");
        assertThat(paused).isFalse();
        
        // Try to get status of non-existent publisher
        OutboxManagementService.PublisherStatus status = outboxManagementService.getPublisherStatus("NonExistentPublisher");
        assertThat(status).isNull();
        
        // Verify publisher doesn't exist
        assertThat(outboxManagementService.publisherExists("NonExistentPublisher")).isFalse();
    }
}
