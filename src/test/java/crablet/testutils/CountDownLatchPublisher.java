package crablet.testutils;

import com.crablet.core.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only publisher that uses CountDownLatch to verify exact event processing counts.
 * Useful for integration tests to ensure deterministic behavior.
 * <p>
 * This publisher is only available in test scope and should NOT be used in production.
 */
@Component
public class CountDownLatchPublisher implements OutboxPublisher {
    
    private final AtomicInteger totalEventsProcessed = new AtomicInteger(0);
    private CountDownLatch latch;
    private volatile boolean healthy = true;
    
    /**
     * Set up a CountDownLatch expecting the given number of events.
     * This should be called before triggering event processing.
     */
    public void expectEvents(int expectedCount) {
        this.latch = new CountDownLatch(expectedCount);
        this.totalEventsProcessed.set(0);
    }
    
    /**
     * Wait for all expected events to be processed.
     * @return true if all events were processed within timeout, false otherwise
     */
    public boolean awaitEvents(long timeoutMs) throws InterruptedException {
        if (latch == null) {
            throw new IllegalStateException("No latch set up. Call expectEvents() first.");
        }
        return latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Get the total number of events processed by this publisher.
     */
    public int getTotalEventsProcessed() {
        return totalEventsProcessed.get();
    }
    
    /**
     * Reset the publisher state.
     */
    public void reset() {
        this.latch = null;
        this.totalEventsProcessed.set(0);
        this.healthy = true;
    }
    
    @Override
    public String getName() {
        return "CountDownLatchPublisher";
    }
    
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        if (!healthy) {
            throw new PublishException("Publisher is in failure state", null);
        }
        
        int batchSize = events.size();
        totalEventsProcessed.addAndGet(batchSize);
        
        // Count down the latch for each event
        for (int i = 0; i < batchSize; i++) {
            if (latch != null) {
                latch.countDown();
            }
        }
        
        // Simulate some processing time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishException("Interrupted during processing", e);
        }
    }
    
    @Override
    public boolean isHealthy() {
        return healthy;
    }
    
    @Override
    public PublishMode getPreferredMode() {
        return PublishMode.BATCH;
    }
}

