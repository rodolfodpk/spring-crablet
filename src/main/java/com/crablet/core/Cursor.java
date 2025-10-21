package com.crablet.core;

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

    /**
     * Fluent builder for constructing cursors.
     */
    public static class Builder {
        private SequenceNumber position;
        private Instant occurredAt;
        private String transactionId;
        
        private Builder() {
            // Defaults for optional fields
            this.position = SequenceNumber.zero();
            this.occurredAt = Instant.now();
            this.transactionId = "0";
        }
        
        private Builder(SequenceNumber position, Instant occurredAt, String transactionId) {
            this.position = position;
            this.occurredAt = occurredAt;
            this.transactionId = transactionId;
        }
        
        public Builder position(long position) {
            this.position = SequenceNumber.of(position);
            return this;
        }
        
        public Builder position(SequenceNumber position) {
            this.position = position;
            return this;
        }
        
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }
        
        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }
        
        public Cursor build() {
            return new Cursor(position, occurredAt, transactionId);
        }
    }

    /**
     * Create a fluent builder for constructing cursors.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a fluent builder starting from a StoredEvent.
     */
    public static Builder from(StoredEvent event) {
        return new Builder(
            SequenceNumber.of(event.position()),
            event.occurredAt(),
            event.transactionId()
        );
    }

    /**
     * Check if this cursor is before another cursor.
     */
    public boolean isBefore(Cursor other) {
        if (!this.position.equals(other.position)) {
            return this.position.isLessThan(other.position);
        }
        return this.occurredAt.isBefore(other.occurredAt);
    }

    /**
     * Check if this cursor is after another cursor.
     */
    public boolean isAfter(Cursor other) {
        if (!this.position.equals(other.position)) {
            return this.position.isGreaterThan(other.position);
        }
        return this.occurredAt.isAfter(other.occurredAt);
    }
}
