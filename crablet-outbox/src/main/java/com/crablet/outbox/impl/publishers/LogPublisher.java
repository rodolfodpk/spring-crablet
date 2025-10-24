package com.crablet.outbox.impl.publishers;

import com.crablet.core.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simple log-based publisher for testing/development.
 * Enabled via: crablet.outbox.publishers.log.enabled=true
 */
@Component
@ConditionalOnProperty(prefix = "crablet.outbox.publishers.log", name = "enabled", havingValue = "true")
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
