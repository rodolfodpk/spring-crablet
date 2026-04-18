package com.crablet.outbox;

/**
 * Identifies an independent outbox processor by the topic it reads from and the publisher that
 * handles delivery.
 * <p>
 * Each {@code (topic, publisher)} combination gets its own poller and leader-election lock, so
 * multiple publishers can consume the same topic independently without blocking each other.
 *
 * @param topic     the outbox topic name; matches the value passed to {@link OutboxPublisher} at
 *                  publish time; must not be null or empty
 * @param publisher the {@link OutboxPublisher#getName() publisher name}; must not be null or empty
 */
public record TopicPublisherPair(String topic, String publisher) {

    /**
     * Creates a topic/publisher pair.
     *
     * @param topic     the outbox topic name; must not be null or empty
     * @param publisher the publisher name; must not be null or empty
     * @throws IllegalArgumentException if either value is null or empty
     */
    public TopicPublisherPair {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be null or empty");
        }
        if (publisher == null || publisher.isEmpty()) {
            throw new IllegalArgumentException("Publisher cannot be null or empty");
        }
    }

    /** Returns {@code "topic:publisher"} for use as the processor ID in management APIs. */
    @Override
    public String toString() {
        return topic + ":" + publisher;
    }

    /** Stable string key for scan-progress persistence. Use this instead of {@link #toString()} to prevent drift. */
    public String toKey() {
        return topic + ":" + publisher;
    }

    /**
     * Returns a stable hash used as the PostgreSQL advisory-lock key for leader election.
     * Unique per {@code (topic, publisher)} combination within a deployment.
     */
    public long getLockKey() {
        return ("crablet-outbox-pair-" + topic + "-" + publisher).hashCode();
    }
}
