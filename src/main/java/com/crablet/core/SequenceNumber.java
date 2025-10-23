package com.crablet.core;

import java.util.Objects;

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

    public boolean isZero() {
        return value == 0;
    }

    public boolean isGreaterThan(SequenceNumber other) {
        return this.value > other.value;
    }

    public boolean isLessThan(SequenceNumber other) {
        return this.value < other.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SequenceNumber that = (SequenceNumber) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
