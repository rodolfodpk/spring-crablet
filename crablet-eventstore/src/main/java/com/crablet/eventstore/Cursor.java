package com.crablet.eventstore;

import java.time.Instant;

/**
 * Cursor represents a position in the event stream.
 * This is a pure data record with no business logic.
 */
public record Cursor(SequenceNumber position, Instant occurredAt, String transactionId) {
    /**
     * Create a cursor from position, timestamp, and transaction ID.
     */
    public static Cursor of(SequenceNumber position, Instant occurredAt, String transactionId) {
        return new Cursor(position, occurredAt, transactionId);
    }

    /**
     * Create a cursor from position and timestamp (transaction ID will be 0).
     */
    public static Cursor of(SequenceNumber position, Instant occurredAt) {
        return new Cursor(position, occurredAt, "0");
    }

    /**
     * Create a cursor from position only (timestamp will be current time, transaction ID will be 0).
     */
    public static Cursor of(SequenceNumber position) {
        return new Cursor(position, Instant.now(), "0");
    }

    /**
     * Create a cursor from long position (timestamp will be current time, transaction ID will be 0).
     */
    public static Cursor of(long position) {
        return new Cursor(SequenceNumber.of(position), Instant.now(), "0");
    }

    /**
     * Create a cursor from long position and timestamp (transaction ID will be 0).
     */
    public static Cursor of(long position, Instant occurredAt) {
        return new Cursor(SequenceNumber.of(position), occurredAt, "0");
    }

    /**
     * Create a cursor from long position, timestamp, and transaction ID.
     */
    public static Cursor of(long position, Instant occurredAt, String transactionId) {
        return new Cursor(SequenceNumber.of(position), occurredAt, transactionId);
    }

    /**
     * Create a zero cursor for empty projections.
     */
    public static Cursor zero() {
        return Cursor.of(SequenceNumber.zero(), Instant.EPOCH, "0");
    }
}
