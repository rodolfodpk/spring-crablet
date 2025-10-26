package com.crablet.outbox.publishers;

import com.crablet.store.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "crablet.outbox.publishers.statistics", name = "enabled", havingValue = "true")
public class StatisticsPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(StatisticsPublisher.class);
    
    private final long logIntervalSeconds;
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final Map<String, AtomicLong> eventTypeCounters = new ConcurrentHashMap<>();
    private volatile Instant lastLogTime = Instant.now();
    private volatile Instant firstEventTime = null;
    
    public StatisticsPublisher(Environment environment) {
        // Read from config with default of 10 seconds
        this.logIntervalSeconds = Long.parseLong(
            environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10")
        );
        log.info("StatisticsPublisher initialized with log interval: {} seconds", logIntervalSeconds);
    }
    
    @Override
    public String getName() {
        return "StatisticsPublisher";
    }
    
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        if (firstEventTime == null) {
            firstEventTime = Instant.now();
        }
        
        // Aggregate statistics
        long batchSize = events.size();
        totalEventsProcessed.addAndGet(batchSize);
        
        // Count by event type
        events.forEach(event -> {
            eventTypeCounters.computeIfAbsent(event.type(), _k -> new AtomicLong(0))
                .incrementAndGet();
        });
        
        // Log statistics periodically
        Instant now = Instant.now();
        Duration sinceLastLog = Duration.between(lastLogTime, now);
        
        if (sinceLastLog.getSeconds() >= logIntervalSeconds) {
            logStatistics(now);
            lastLogTime = now;
        }
    }
    
    private void logStatistics(Instant now) {
        Duration totalDuration = Duration.between(firstEventTime, now);
        long total = totalEventsProcessed.get();
        
        double eventsPerSecond = total / (double) Math.max(1, totalDuration.getSeconds());
        
        log.info("===== Outbox Statistics =====");
        log.info("Total events processed: {}", total);
        log.info("Throughput: {:.2f} events/sec", eventsPerSecond);
        log.info("Uptime: {} seconds", totalDuration.getSeconds());
        
        if (!eventTypeCounters.isEmpty()) {
            log.info("Events by type:");
            eventTypeCounters.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    long count = entry.getValue().get();
                    double percentage = (count * 100.0) / total;
                    log.info("  - {}: {} ({:.1f}%)", entry.getKey(), count, percentage);
                });
        }
        
        log.info("=============================");
    }
    
    @Override
    public PublishMode getPreferredMode() {
        return PublishMode.BATCH;
    }
    
    @Override
    public boolean isHealthy() {
        return true; // Always healthy, just logging
    }
}

