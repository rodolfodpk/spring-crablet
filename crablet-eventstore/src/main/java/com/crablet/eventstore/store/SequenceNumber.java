package com.crablet.eventstore.store;

/**
 * SequenceNumber represents the sequence number of an event in the event store.
 * This is equivalent to the Go SequenceNumber type.
 */
public record SequenceNumber(long value) {

    public static SequenceNumber of(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("SequenceNumber cannot be negative");
        }
        return new SequenceNumber(value);
    }

    public static SequenceNumber zero() {
        return new SequenceNumber(0);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
