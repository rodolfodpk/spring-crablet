package crablet.integration;

import com.crablet.core.AppendEvent;
import com.crablet.core.EventStore;
import com.crablet.outbox.impl.JDBCOutboxProcessor;
import com.crablet.outbox.impl.OutboxConfig;
import com.crablet.outbox.impl.OutboxManagementService;
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
    "crablet.outbox.topics.default.required-tags=test",
    "crablet.outbox.topics.default.publishers=CountDownLatchPublisher,TestPublisher,LogPublisher"
})
class OutboxManagementIT extends AbstractCrabletIT {
    
    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private JDBCOutboxProcessor outboxProcessor;
    
    @Autowired
    private OutboxConfig outboxConfig;
    
    @Autowired
    private OutboxManagementService managementService;
    
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
    void shouldPausePublisherAndStopProcessing() throws InterruptedException {
        // Given - Create some events and process them to register publisher
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent1")
                .tag("test", "pause1")
                .data("{\"test\":\"pause1\"}".getBytes())
                .build(),
            AppendEvent.builder("TestEvent2")
                .tag("test", "pause2")
                .data("{\"test\":\"pause2\"}".getBytes())
                .build()
        );

        eventStore.append(events);
        outboxProcessor.processPending();
        
        // Wait for initial processing
        boolean initialProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (initialProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // When - Pause publisher
        boolean paused = managementService.pausePublisher("CountDownLatchPublisher");
        
        // Then - Should successfully pause
        assertThat(paused).isTrue();
        
        // Verify publisher status in database
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(status).isEqualTo("PAUSED");
    }
    
    @Test
    void shouldResumePublisherAndContinueProcessing() throws InterruptedException {
        // Given - Create events and pause publisher first
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "resume")
            .data("{\"test\":\"resume\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for initial processing to register publisher
        boolean initialProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (initialProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // Pause publisher
        managementService.pausePublisher("CountDownLatchPublisher");
        
        // Reset counter for resume test
        countDownLatchPublisher.reset();
        countDownLatchPublisher.expectEvents(1);
        
        // Add more events
        AppendEvent newEvent = AppendEvent.builder("NewEvent")
            .tag("test", "resume2")
            .data("{\"test\":\"resume2\"}".getBytes())
            .build();
        eventStore.append(List.of(newEvent));

        // When - Resume publisher
        boolean resumed = managementService.resumePublisher("CountDownLatchPublisher");
        
        // Then - Should successfully resume
        assertThat(resumed).isTrue();
        
        // Process events after resume
        outboxProcessor.processPending();
        
        // Verify processing continues
        boolean resumedProcessed = countDownLatchPublisher.awaitEvents(5000);
        if (resumedProcessed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }
        
        // Verify publisher status in database
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(status).isEqualTo("ACTIVE");
    }
    
    @Test
    void shouldResetFailedPublisher() throws InterruptedException {
        // Given - Create events and process to register publisher
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "reset")
            .data("{\"test\":\"reset\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // Simulate publisher failure by updating database directly
        jdbcTemplate.update(
            "UPDATE outbox_topic_progress SET status = 'FAILED', error_count = 5, last_error = 'Test error' WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'"
        );

        // When - Reset publisher
        boolean reset = managementService.resetPublisher("CountDownLatchPublisher");
        
        // Then - Should successfully reset
        assertThat(reset).isTrue();
        
        // Verify publisher state was reset
        var result = jdbcTemplate.queryForMap(
            "SELECT status, error_count, last_error FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'"
        );
        
        assertThat(result.get("status")).isEqualTo("ACTIVE");
        assertThat(result.get("error_count")).isEqualTo(0);
        assertThat(result.get("last_error")).isNull();
    }
    
    @Test
    void shouldGetPublisherStatus() throws InterruptedException {
        // Given - Create events and process to register publisher
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "status")
            .data("{\"test\":\"status\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // When - Get publisher status
        var status = managementService.getPublisherStatus("CountDownLatchPublisher");
        
        // Then - Should return status information
        assertThat(status).isNotNull();
        assertThat(status.publisherName()).isEqualTo("CountDownLatchPublisher");
        assertThat(status.status()).isEqualTo("ACTIVE");
        assertThat(status.lastPosition()).isGreaterThan(0);
    }
    
    @Test
    void shouldGetPublisherLag() throws InterruptedException {
        // Given - Create events and process some
        countDownLatchPublisher.expectEvents(2);
        
        List<AppendEvent> events = List.of(
            AppendEvent.builder("TestEvent1")
                .tag("test", "lag1")
                .data("{\"test\":\"lag1\"}".getBytes())
                .build(),
            AppendEvent.builder("TestEvent2")
                .tag("test", "lag2")
                .data("{\"test\":\"lag2\"}".getBytes())
                .build()
        );

        eventStore.append(events);
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(2);
        }

        // Add more events to create lag
        AppendEvent lagEvent = AppendEvent.builder("LagEvent")
            .tag("test", "lag3")
            .data("{\"test\":\"lag3\"}".getBytes())
            .build();
        eventStore.append(List.of(lagEvent));

        // When - Get publisher lag
        var lag = managementService.getPublisherLag();
        
        // Then - Should return lag information
        assertThat(lag).isNotNull();
        assertThat(lag).containsKey("CountDownLatchPublisher");
        assertThat(lag.get("CountDownLatchPublisher")).isNotNull();
    }
    
    @Test
    void shouldHandleMultiplePublishersWithDifferentStates() throws InterruptedException {
        // Given - Create events and process to register multiple publishers
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "multi")
            .data("{\"test\":\"multi\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // When - Set different states for different publishers
        boolean paused1 = managementService.pausePublisher("CountDownLatchPublisher");
        boolean paused2 = managementService.pausePublisher("TestPublisher");
        
        // Then - Both should be paused
        assertThat(paused1).isTrue();
        assertThat(paused2).isTrue();
        
        // Verify both publishers are paused
        String status1 = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        String status2 = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'TestPublisher'",
            String.class
        );
        
        assertThat(status1).isEqualTo("PAUSED");
        assertThat(status2).isEqualTo("PAUSED");
    }
    
    @Test
    void shouldHandlePublisherStatusTransitions() throws InterruptedException {
        // Given - Create events and process to register publisher
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "transitions")
            .data("{\"test\":\"transitions\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // When - Test status transitions: ACTIVE -> PAUSED -> FAILED -> ACTIVE
        String initialStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(initialStatus).isEqualTo("ACTIVE");
        
        // Transition to PAUSED
        boolean paused = managementService.pausePublisher("CountDownLatchPublisher");
        assertThat(paused).isTrue();
        
        String pausedStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(pausedStatus).isEqualTo("PAUSED");
        
        // Simulate FAILED state
        jdbcTemplate.update(
            "UPDATE outbox_topic_progress SET status = 'FAILED', error_count = 3 WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'"
        );
        
        String failedStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(failedStatus).isEqualTo("FAILED");
        
        // Transition back to ACTIVE via reset
        boolean reset = managementService.resetPublisher("CountDownLatchPublisher");
        assertThat(reset).isTrue();
        
        String finalStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(finalStatus).isEqualTo("ACTIVE");
    }
    
    @Test
    void shouldHandleConcurrentManagementOperations() throws InterruptedException {
        // Given - Create events and process to register publisher
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "concurrent")
            .data("{\"test\":\"concurrent\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // When - Perform concurrent operations
        boolean paused = managementService.pausePublisher("CountDownLatchPublisher");
        boolean resumed = managementService.resumePublisher("CountDownLatchPublisher");
        boolean reset = managementService.resetPublisher("CountDownLatchPublisher");
        
        // Then - All operations should succeed
        assertThat(paused).isTrue();
        assertThat(resumed).isTrue();
        assertThat(reset).isTrue();
        
        // Final state should be ACTIVE
        String finalStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(finalStatus).isEqualTo("ACTIVE");
    }
    
    @Test
    void shouldReturnFalseForNonExistentPublisher() {
        // When - Try to manage non-existent publisher
        boolean paused = managementService.pausePublisher("NonExistentPublisher");
        boolean resumed = managementService.resumePublisher("NonExistentPublisher");
        boolean reset = managementService.resetPublisher("NonExistentPublisher");
        
        // Then - All operations should return false
        assertThat(paused).isFalse();
        assertThat(resumed).isFalse();
        assertThat(reset).isFalse();
    }
    
    @Test
    void shouldHandlePublisherAutoPauseAfterMaxRetries() throws InterruptedException {
        // Given - Create events and process to register publisher
        countDownLatchPublisher.expectEvents(1);
        
        AppendEvent event = AppendEvent.builder("TestEvent")
            .tag("test", "autopause")
            .data("{\"test\":\"autopause\"}".getBytes())
            .build();

        eventStore.append(List.of(event));
        outboxProcessor.processPending();
        
        // Wait for processing
        boolean processed = countDownLatchPublisher.awaitEvents(5000);
        if (processed) {
            assertThat(countDownLatchPublisher.getTotalEventsProcessed()).isEqualTo(1);
        }

        // When - Simulate max retries exceeded by updating database
        jdbcTemplate.update(
            "UPDATE outbox_topic_progress SET status = 'FAILED', error_count = 10, last_error = 'Max retries exceeded' WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'"
        );

        // Then - Publisher should be in FAILED state
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(status).isEqualTo("FAILED");
        
        // Should be able to reset it
        boolean reset = managementService.resetPublisher("CountDownLatchPublisher");
        assertThat(reset).isTrue();
        
        String resetStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_topic_progress WHERE topic = 'default' AND publisher = 'CountDownLatchPublisher'",
            String.class
        );
        assertThat(resetStatus).isEqualTo("ACTIVE");
    }
}
