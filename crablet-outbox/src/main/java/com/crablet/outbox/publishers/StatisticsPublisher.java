package com.crablet.outbox.publishers;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publisher that logs statistics about event publishing.
 * Users must define as @Bean in Spring configuration.
 */
public class StatisticsPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(StatisticsPublisher.class);
    
    private final long logIntervalSeconds;
    private final ClockProvider clockProvider;
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final Map<String, AtomicLong> eventTypeCounters = new ConcurrentHashMap<>();
    private volatile Instant lastLogTime;
    private volatile @Nullable Instant firstEventTime = null;
    
    public StatisticsPublisher(Environment environment) {
        this(environment, ClockProvider.systemDefault());
    }

    public StatisticsPublisher(Environment environment, ClockProvider clockProvider) {
        // Read from config with default of 10 seconds
        this.logIntervalSeconds = Long.parseLong(
            environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10")
        );
        this.clockProvider = clockProvider;
        this.lastLogTime = clockProvider.now();
        log.info("StatisticsPublisher initialized with log interval: {} seconds", logIntervalSeconds);
    }
    
    @Override
    public String getName() {
        return "StatisticsPublisher";
    }
    
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        if (firstEventTime == null) {
            firstEventTime = clockProvider.now();
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
        Instant now = clockProvider.now();
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
        log.info("Throughput: {} events/sec", String.format(Locale.ROOT, "%.2f", eventsPerSecond));
        log.info("Uptime: {} seconds", totalDuration.getSeconds());
        
        if (!eventTypeCounters.isEmpty()) {
            log.info("Events by type:");
            eventTypeCounters.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    long count = entry.getValue().get();
                    double percentage = (count * 100.0) / total;
                    log.info("  - {}: {} ({}%)", entry.getKey(), count,
                            String.format(Locale.ROOT, "%.1f", percentage));
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
