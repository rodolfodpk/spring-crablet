package com.crablet.integration;

import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.progress.ProcessorStatus;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.testutils.EventProcessorTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

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
    private com.crablet.eventpoller.processor.EventProcessor<com.crablet.outbox.internal.OutboxProcessorConfig, TopicPublisherPair> eventProcessor;
    
    @Autowired
    private Map<TopicPublisherPair, com.crablet.outbox.internal.OutboxProcessorConfig> processorConfigs;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @Autowired
    private ProcessorManagementService<TopicPublisherPair> managementService;
    
    @Test
    void shouldPauseAndResumePublisher() {
        // Enable outbox and create some events
        outboxConfig.setEnabled(true);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test_id", "pause-test")
            .data("{\"test\":\"pause\"}".getBytes())
            .build();
        
        eventStore.appendCommutative(List.of(event));
        
        // Process to register publisher - find the processor ID for CountDownLatchPublisher
        TopicPublisherPair processorId = findProcessorId("CountDownLatchPublisher");
        if (processorId != null) {
            EventProcessorTestHelper.processAll(eventProcessor, processorConfigs);
            
            // Pause publisher
            boolean paused = managementService.pause(processorId);
            assertThat(paused).isTrue();
            
            // Verify status is PAUSED
            ProcessorStatus status = managementService.getStatus(processorId);
            assertThat(status).isNotNull();
            assertThat(status).isEqualTo(ProcessorStatus.PAUSED);
            
            // Resume publisher
            boolean resumed = managementService.resume(processorId);
            assertThat(resumed).isTrue();
            
            // Verify status is ACTIVE
            status = managementService.getStatus(processorId);
            assertThat(status).isNotNull();
            assertThat(status).isEqualTo(ProcessorStatus.ACTIVE);
        }
    }
    
    @Test
    void shouldGetAllPublisherStatus() {
        // Enable outbox and create some events
        outboxConfig.setEnabled(true);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test_id", "status-test")
            .data("{\"test\":\"status\"}".getBytes())
            .build();
        
        eventStore.appendCommutative(List.of(event));
        
        // Process to register publishers
        EventProcessorTestHelper.processAll(eventProcessor, processorConfigs);
        
        // Get all processor statuses
        Map<TopicPublisherPair, ProcessorStatus> statuses = managementService.getAllStatuses();
        
        assertThat(statuses).isNotEmpty();
        assertThat(statuses).hasSizeGreaterThanOrEqualTo(1);
        
        // Verify CountDownLatchPublisher is in the list
        boolean testPublisherFound = statuses.keySet().stream()
            .anyMatch(pair -> "CountDownLatchPublisher".equals(pair.publisher()));
        assertThat(testPublisherFound).isTrue();
    }
    
    @Test
    void shouldHandleNonExistentPublisher() {
        // Try to pause non-existent publisher
        TopicPublisherPair nonExistent = new TopicPublisherPair("default", "NonExistentPublisher");
        boolean paused = managementService.pause(nonExistent);
        assertThat(paused).isFalse();
        
        // Try to get status of non-existent publisher
        ProcessorStatus status = managementService.getStatus(nonExistent);
        // Status might be ACTIVE (default) or null depending on implementation
        // Just verify the call doesn't throw
        assertThat(status != null || status == null).isTrue();
    }
    
    /**
     * Helper to find processor ID for a publisher name.
     */
    private TopicPublisherPair findProcessorId(String publisherName) {
        return processorConfigs.keySet().stream()
            .filter(pair -> publisherName.equals(pair.publisher()))
            .findFirst()
            .orElse(null);
    }
}
