package com.crablet.outbox.publishing;

import com.crablet.eventstore.StoredEvent;

import java.util.List;

/**
 * Service responsible for publishing outbox event batches to a configured publisher.
 */
public interface OutboxPublishingService {

    /**
     * Publish the batch already fetched by the generic event processor.
     *
     * @param topicName Topic name
     * @param publisherName Publisher name
     * @param events Events to publish
     * @return number of events published
     * @throws RuntimeException if publishing fails
     */
    int publish(String topicName, String publisherName, List<StoredEvent> events);
}
