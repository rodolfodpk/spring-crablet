package com.crablet.outbox.publishers;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple log-based publisher for testing/development.
 * Users must define as @Bean in Spring configuration.
 */
public class LogPublisher implements OutboxPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(LogPublisher.class);
    
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        log.info("Publishing batch of {} events:", events.size());
        for (StoredEvent event : events) {
            log.info("  [{}] {} @ position {}", 
                event.type(), 
                new String(event.data()), 
                event.position()
            );
        }
    }
    
    @Override
    public String getName() {
        return "LogPublisher";
    }
    
    @Override
    public boolean isHealthy() {
        return true;
    }
}
