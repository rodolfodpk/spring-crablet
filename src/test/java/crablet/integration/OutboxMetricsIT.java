package crablet.integration;
import static crablet.testutils.DCBTestHelpers.*;

import io.micrometer.core.instrument.MeterRegistry;
import com.crablet.outbox.impl.OutboxMetrics;
import com.crablet.outbox.impl.OutboxPublisherMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import crablet.integration.AbstractCrabletIT;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMetricsIT extends AbstractCrabletIT {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private OutboxMetrics outboxMetrics;
    
    @Autowired
    private OutboxPublisherMetrics publisherMetrics;
    
    @Test
    void shouldRecordMetrics() {
        // Record some events
        outboxMetrics.recordEventsPublished("TestPublisher", 10);
        outboxMetrics.recordProcessingCycle();
        outboxMetrics.setLeader(true);
        
        // Verify metrics exist
        assertThat(meterRegistry.find("outbox.events.published").counter()).isNotNull();
        assertThat(meterRegistry.find("outbox.processing.cycles").counter()).isNotNull();
        assertThat(meterRegistry.find("outbox.is_leader").gauge()).isNotNull();
    }
    
    @Test
    void shouldRecordPublisherMetrics() {
        // Start publishing timer
        var sample = publisherMetrics.startPublishing("TestPublisher");
        
        // Simulate some work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Record success
        publisherMetrics.recordPublishingSuccess("TestPublisher", sample, 5);
        
        // Verify metrics exist
        assertThat(meterRegistry.find("outbox.publishing.duration").timer()).isNotNull();
        assertThat(meterRegistry.find("outbox.events.published.by_publisher").counter()).isNotNull();
    }
    
    @Test
    void shouldRecordPublisherErrors() {
        // Record an error
        publisherMetrics.recordPublishingError("TestPublisher");
        
        // Verify error counter exists
        assertThat(meterRegistry.find("outbox.errors.by_publisher").counter()).isNotNull();
    }
    
    @Test
    void shouldHaveInstanceId() {
        // Verify instance ID is set
        String instanceId = outboxMetrics.getInstanceId();
        assertThat(instanceId).isNotNull();
        assertThat(instanceId).isNotEmpty();
    }
}
