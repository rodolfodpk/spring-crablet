package com.crablet.outbox.publishing;

/**
 * Service responsible for fetching, publishing, and updating position for outbox events.
 * Separated from scheduling logic to improve separation of concerns.
 */
public interface OutboxPublishingService {
    
    /**
     * Fetch, publish, and update position for a (topic, publisher) pair.
     * 
     * @param topicName Topic name
     * @param publisherName Publisher name
     * @return number of events published
     * @throws RuntimeException if publishing fails
     */
    int publishForTopicPublisher(String topicName, String publisherName);
}

