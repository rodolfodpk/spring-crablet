package com.crablet.outbox.publishers;

import com.crablet.outbox.config.GlobalStatisticsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global statistics publisher that tracks all outbox events across all topics and publishers.
 * 
 * This component provides comprehensive monitoring of the outbox system by tracking:
 * - Global totals (total events, throughput, uptime)
 * - Per-topic breakdown (events per topic)
 * - Per-publisher breakdown (events per publisher, per topic-publisher pair)
 * 
 * Unlike the regular StatisticsPublisher, this runs globally and is not tied to
 * specific topic configurations.
 */
@Component
@ConditionalOnProperty(prefix = "crablet.outbox.global-statistics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobalStatisticsPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalStatisticsPublisher.class);
    
    private final GlobalStatisticsConfig config;
    
    // Global statistics
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private volatile Instant firstEventTime = null;
    private volatile Instant lastLogTime = Instant.now();
    
    // Per-topic statistics
    private final Map<String, AtomicLong> eventsPerTopic = new ConcurrentHashMap<>();
    
    // Per-publisher statistics
    private final Map<String, AtomicLong> eventsPerPublisher = new ConcurrentHashMap<>();
    
    // Per topic-publisher pair statistics
    private final Map<String, AtomicLong> eventsPerTopicPublisher = new ConcurrentHashMap<>();
    
    // Event type statistics (global)
    private final Map<String, AtomicLong> eventsPerType = new ConcurrentHashMap<>();
    
    @Autowired
    public GlobalStatisticsPublisher(GlobalStatisticsConfig config) {
        this.config = config;
        log.info("GlobalStatisticsPublisher initialized with log interval: {} seconds", 
                config.getLogIntervalSeconds());
    }
    
    /**
     * Record an event being processed by the outbox system.
     * 
     * @param topic The topic name
     * @param publisher The publisher name
     * @param eventType The event type
     */
    public void recordEvent(String topic, String publisher, String eventType) {
        if (firstEventTime == null) {
            firstEventTime = Instant.now();
        }
        
        // Increment global counter
        totalEventsProcessed.incrementAndGet();
        
        // Increment per-topic counter
        eventsPerTopic.computeIfAbsent(topic, _k -> new AtomicLong(0)).incrementAndGet();
        
        // Increment per-publisher counter
        eventsPerPublisher.computeIfAbsent(publisher, _k -> new AtomicLong(0)).incrementAndGet();
        
        // Increment per topic-publisher pair counter
        String topicPublisherKey = topic + ":" + publisher;
        eventsPerTopicPublisher.computeIfAbsent(topicPublisherKey, _k -> new AtomicLong(0)).incrementAndGet();
        
        // Increment per-event-type counter
        eventsPerType.computeIfAbsent(eventType, _k -> new AtomicLong(0)).incrementAndGet();
        
        // Check if it's time to log statistics
        Instant now = Instant.now();
        Duration sinceLastLog = Duration.between(lastLogTime, now);
        
        if (sinceLastLog.getSeconds() >= config.getLogIntervalSeconds()) {
            logStatistics(now);
            lastLogTime = now;
        }
    }
    
    /**
     * Log comprehensive statistics about the outbox system.
     */
    private void logStatistics(Instant now) {
        Duration totalDuration = Duration.between(firstEventTime, now);
        long total = totalEventsProcessed.get();
        
        if (total == 0) {
            return; // No events processed yet
        }
        
        double eventsPerSecond = total / (double) Math.max(1, totalDuration.getSeconds());
        
        log.info("===== Global Outbox Statistics =====");
        log.info("Total events processed: {}", total);
        log.info("Throughput: {:.2f} events/sec", eventsPerSecond);
        log.info("Uptime: {} seconds", totalDuration.getSeconds());
        
        // Log per-topic statistics
        if (!eventsPerTopic.isEmpty()) {
            log.info("Events by topic:");
            eventsPerTopic.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    long count = entry.getValue().get();
                    double percentage = (count * 100.0) / total;
                    log.info("  - {}: {} ({:.1f}%)", entry.getKey(), count, percentage);
                });
        }
        
        // Log per-publisher statistics
        if (!eventsPerPublisher.isEmpty()) {
            log.info("Events by publisher:");
            eventsPerPublisher.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    long count = entry.getValue().get();
                    double percentage = (count * 100.0) / total;
                    log.info("  - {}: {} ({:.1f}%)", entry.getKey(), count, percentage);
                });
        }
        
        // Log per topic-publisher pair statistics (top 10)
        if (!eventsPerTopicPublisher.isEmpty()) {
            log.info("Top topic-publisher pairs:");
            eventsPerTopicPublisher.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(10)
                .forEach(entry -> {
                    long count = entry.getValue().get();
                    double percentage = (count * 100.0) / total;
                    log.info("  - {}: {} ({:.1f}%)", entry.getKey(), count, percentage);
                });
        }
        
        // Log per-event-type statistics
        if (!eventsPerType.isEmpty()) {
            log.info("Events by type:");
            eventsPerType.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    long count = entry.getValue().get();
                    double percentage = (count * 100.0) / total;
                    log.info("  - {}: {} ({:.1f}%)", entry.getKey(), count, percentage);
                });
        }
        
        log.info("=====================================");
    }
    
    /**
     * Get current total events processed.
     */
    public long getTotalEventsProcessed() {
        return totalEventsProcessed.get();
    }
    
    /**
     * Get events processed for a specific topic.
     */
    public long getEventsForTopic(String topic) {
        return eventsPerTopic.getOrDefault(topic, new AtomicLong(0)).get();
    }
    
    /**
     * Get events processed by a specific publisher.
     */
    public long getEventsForPublisher(String publisher) {
        return eventsPerPublisher.getOrDefault(publisher, new AtomicLong(0)).get();
    }
    
    /**
     * Get events processed for a specific topic-publisher pair.
     */
    public long getEventsForTopicPublisher(String topic, String publisher) {
        String key = topic + ":" + publisher;
        return eventsPerTopicPublisher.getOrDefault(key, new AtomicLong(0)).get();
    }
    
    /**
     * Get events processed of a specific type.
     */
    public long getEventsForType(String eventType) {
        return eventsPerType.getOrDefault(eventType, new AtomicLong(0)).get();
    }
    
    /**
     * Reset all statistics (useful for testing).
     */
    public void reset() {
        totalEventsProcessed.set(0);
        firstEventTime = null;
        lastLogTime = Instant.now();
        eventsPerTopic.clear();
        eventsPerPublisher.clear();
        eventsPerTopicPublisher.clear();
        eventsPerType.clear();
    }
}
