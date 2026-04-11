package com.crablet.outbox.publishing;

import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.PublishingDurationMetric;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Implementation of OutboxPublishingService.
 * Publishes the event batch already fetched by the generic event processor.
 */
public class OutboxPublishingServiceImpl implements OutboxPublishingService {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxPublishingServiceImpl.class);
    
    private final Map<String, OutboxPublisher> publisherByName;
    private final ClockProvider clock;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final GlobalStatisticsPublisher globalStatistics;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Creates a new OutboxPublishingServiceImpl.
     *
     * @param publisherByName map of publisher names to publisher implementations
     * @param clock clock provider for timestamps
     * @param circuitBreakerRegistry circuit breaker registry for resilience
     * @param globalStatistics global statistics publisher (required)
     * @param eventPublisher event publisher for metrics (required).
     *                       Spring Boot automatically provides an ApplicationEventPublisher bean.
     *                       See crablet-metrics-micrometer for automatic metrics collection.
     */
    public OutboxPublishingServiceImpl(
            Map<String, OutboxPublisher> publisherByName,
            ClockProvider clock,
            CircuitBreakerRegistry circuitBreakerRegistry,
            GlobalStatisticsPublisher globalStatistics,
            ApplicationEventPublisher eventPublisher) {
        if (publisherByName == null) {
            throw new IllegalArgumentException("publisherByName must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (circuitBreakerRegistry == null) {
            throw new IllegalArgumentException("circuitBreakerRegistry must not be null");
        }
        if (globalStatistics == null) {
            throw new IllegalArgumentException("globalStatistics must not be null");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.publisherByName = publisherByName;
        this.clock = clock;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.globalStatistics = globalStatistics;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public int publish(String topicName, String publisherName, List<StoredEvent> events) {
        // Get publisher implementation
        OutboxPublisher publisher = publisherByName.get(publisherName);
        if (publisher == null) {
            log.warn("Publisher '{}' not found for pair ({}, {})", publisherName, topicName, publisherName);
            throw new RuntimeException("Publisher not found: " + publisherName);
        }

        if (events.isEmpty()) {
            return 0;
        }
        
        // Publish with resilience
        Instant startTime = clock.now();
        try {
            publishWithResilience(publisher, events);
            
            Duration duration = Duration.between(startTime, clock.now());
            eventPublisher.publishEvent(new EventsPublishedMetric(publisherName, events.size()));
            eventPublisher.publishEvent(new PublishingDurationMetric(publisherName, duration));

            // Record events in global statistics
            events.forEach(event -> 
                globalStatistics.recordEvent(topicName, publisherName, event.type()));
            
            return events.size();
        } catch (Exception e) {
            eventPublisher.publishEvent(new OutboxErrorMetric(publisherName));
            throw new RuntimeException("Failed to publish events for pair (" + topicName + ", " + publisherName + ")", e);
        }
    }
    
    private void publishWithResilience(OutboxPublisher publisher, List<StoredEvent> events)
            throws PublishException {
        
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("outbox-" + publisher.getName());
        
        try {
            Callable<Void> call = CircuitBreaker.decorateCallable(cb, () -> {
                publisher.publishBatch(events);
                return null;
            });
            
            call.call();
            
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for {}", publisher.getName());
            throw new PublishException("Circuit breaker open", e);
        } catch (Exception e) {
            throw new PublishException("Publish failed", e);
        }
    }
}
