package com.crablet.outbox.adapter;

/**
 * Identifier for an outbox processor: (topic, publisher) pair.
 * Each pair processes events independently.
 */
public record TopicPublisherPair(String topic, String publisher) {
    /**
     * Create a pair from topic and publisher names.
     */
    public TopicPublisherPair {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be null or empty");
        }
        if (publisher == null || publisher.isEmpty()) {
            throw new IllegalArgumentException("Publisher cannot be null or empty");
        }
    }
    
    /**
     * Get a string representation for logging/debugging.
     */
    @Override
    public String toString() {
        return "(" + topic + ", " + publisher + ")";
    }
}

