package com.crablet.outbox.internal;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.publishing.OutboxPublishingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.util.List;

/**
 * Event handler for outbox processors.
 * Delegates to OutboxPublishingService to publish events externally.
 * 
 * <p><strong>Note:</strong> This handler ignores the writeDataSource parameter
 * since outbox publishes to external systems and doesn't need database writes here.
 */
public class OutboxEventHandler implements EventHandler<TopicPublisherPair> {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxEventHandler.class);
    
    private final OutboxPublishingService publishingService;
    
    public OutboxEventHandler(OutboxPublishingService publishingService) {
        this.publishingService = publishingService;
    }
    
    @Override
    public int handle(TopicPublisherPair processorId, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
        String topicName = processorId.topic();
        String publisherName = processorId.publisher();

        try {
            int published = publishingService.publish(topicName, publisherName, events);
            log.debug("Published {} events for {}", published, processorId);
            return published;
        } catch (Exception e) {
            log.error("Failed to publish events for {}", processorId, e);
            throw e;
        }
    }
}
