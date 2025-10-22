package com.crablet.outbox;

/**
 * Represents a (topic, publisher) pair for independent processing.
 */
public record TopicPublisherPair(String topic, String publisher) {
    
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
