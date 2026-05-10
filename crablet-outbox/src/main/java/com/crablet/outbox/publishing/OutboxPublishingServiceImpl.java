package com.crablet.outbox.publishing;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import com.crablet.outbox.metrics.EventsPublishedMetric;
import com.crablet.outbox.metrics.OutboxErrorMetric;
import com.crablet.outbox.metrics.PublishingDurationMetric;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implementation of OutboxPublishingService.
 * Publishes the event batch already fetched by the generic event processor.
 */
public class OutboxPublishingServiceImpl implements OutboxPublishingService {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxPublishingServiceImpl.class);
    
    private final Map<String, OutboxPublisher> publisherByName;
    private final ClockProvider clock;
    private final GlobalStatisticsPublisher globalStatistics;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Creates a new OutboxPublishingServiceImpl.
     *
     * @param publisherByName map of publisher names to publisher implementations
     * @param clock clock provider for timestamps
     * @param globalStatistics global statistics publisher (required)
     * @param eventPublisher event publisher for metrics (required).
     *                       Spring Boot automatically provides an ApplicationEventPublisher bean.
     *                       See crablet-metrics-micrometer for automatic metrics collection.
     */
    public OutboxPublishingServiceImpl(
            Map<String, OutboxPublisher> publisherByName,
            ClockProvider clock,
            GlobalStatisticsPublisher globalStatistics,
            ApplicationEventPublisher eventPublisher) {
        if (publisherByName == null) {
            throw new IllegalArgumentException("publisherByName must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (globalStatistics == null) {
            throw new IllegalArgumentException("globalStatistics must not be null");
        }
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.publisherByName = publisherByName;
        this.clock = clock;
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
        
        Instant startTime = clock.now();
        try {
            publishWithMode(publisher, events);
            
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

    private void publishWithMode(OutboxPublisher publisher, List<StoredEvent> events)
            throws PublishException {

        if (publisher.getPreferredMode() == OutboxPublisher.PublishMode.INDIVIDUAL) {
            for (StoredEvent event : events) {
                publish(publisher, List.of(event));
            }
            return;
        }

        publish(publisher, events);
    }

    private void publish(OutboxPublisher publisher, List<StoredEvent> events) throws PublishException {
        try {
            publisher.publishBatch(events);
        } catch (Exception e) {
            throw new PublishException("Publish failed", e);
        }
    }
}
