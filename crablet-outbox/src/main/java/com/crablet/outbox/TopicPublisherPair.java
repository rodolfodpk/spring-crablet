package com.crablet.outbox;

/**
 * Represents a (topic, publisher) pair for independent processing.
 */
public record TopicPublisherPair(String topic, String publisher) {

    public TopicPublisherPair {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be null or empty");
        }
        if (publisher == null || publisher.isEmpty()) {
            throw new IllegalArgumentException("Publisher cannot be null or empty");
        }
    }

    @Override
    public String toString() {
        return topic + ":" + publisher;
    }
    
    /**
     * Generate a unique lock key for this pair.
     */
    public long getLockKey() {
        return ("crablet-outbox-pair-" + topic + "-" + publisher).hashCode();
    }
}
