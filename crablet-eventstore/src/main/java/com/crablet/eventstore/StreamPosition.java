package com.crablet.eventstore;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * StreamPosition represents a position in the event stream.
 * This is a pure data record with no business logic.
 */
public record StreamPosition(long position, @Nullable Instant occurredAt, @Nullable String transactionId) {

    public StreamPosition {
        if (position < 0) {
            throw new IllegalArgumentException("StreamPosition cannot be negative");
        }
    }

    public static StreamPosition of(long position, Instant occurredAt, String transactionId) {
        return new StreamPosition(position, occurredAt, transactionId);
    }

    public static StreamPosition of(long position, Instant occurredAt) {
        return new StreamPosition(position, occurredAt, "0");
    }

    public static StreamPosition zero() {
        return new StreamPosition(0L, Instant.EPOCH, "0");
    }
}
