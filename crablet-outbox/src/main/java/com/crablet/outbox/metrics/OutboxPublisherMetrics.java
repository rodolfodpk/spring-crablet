package com.crablet.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics collection for Outbox publishers.
 * Users must define as @Bean in Spring configuration.
 */
public class OutboxPublisherMetrics {
    
    private final MeterRegistry registry;
    private final Map<String, Timer> publishingTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> publishedCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    
    public OutboxPublisherMetrics(MeterRegistry registry) {
        this.registry = registry;
    }
    
    public Timer.Sample startPublishing(String publisherName) {
        return Timer.start(registry);
    }
    
    public void recordPublishingSuccess(String publisherName, Timer.Sample sample, int eventCount) {
        // Record latency
        Timer timer = publishingTimers.computeIfAbsent(publisherName, name ->
            Timer.builder("outbox.publishing.duration")
                .description("Time taken to publish events")
                .tag("publisher", name)
                .register(registry)
        );
        sample.stop(timer);
        
        // Record count
        Counter counter = publishedCounters.computeIfAbsent(publisherName, name ->
            Counter.builder("outbox.events.published.by_publisher")
                .description("Events published by specific publisher")
                .tag("publisher", name)
                .register(registry)
        );
        counter.increment(eventCount);
    }
    
    public void recordPublishingError(String publisherName) {
        Counter counter = errorCounters.computeIfAbsent(publisherName, name ->
            Counter.builder("outbox.errors.by_publisher")
                .description("Publishing errors by publisher")
                .tag("publisher", name)
                .register(registry)
        );
        counter.increment();
    }
}
