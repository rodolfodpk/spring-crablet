package com.crablet.outbox.publishers;

import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
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
            log.info("  [{}] {} @ position {} correlationId={} causationId={}",
                event.type(), 
                new String(event.data(), StandardCharsets.UTF_8),
                event.position(),
                event.correlationId(),
                event.causationId()
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
