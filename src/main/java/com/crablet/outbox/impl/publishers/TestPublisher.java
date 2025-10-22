package com.crablet.outbox.impl.publishers;

import com.crablet.core.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Test publisher for integration tests.
 * Always enabled, no conditional properties.
 */
@Component
public class TestPublisher implements OutboxPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(TestPublisher.class);
    
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        log.info("TestPublisher: Publishing batch of {} events:", events.size());
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
        return "TestPublisher";
    }
    
    @Override
    public boolean isHealthy() {
        return true;
    }
}
