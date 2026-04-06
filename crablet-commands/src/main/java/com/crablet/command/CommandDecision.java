package com.crablet.command;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;

import java.util.List;

/**
 * Sealed decision type for command handling.
 * Each variant corresponds to one of the three DCB concurrency patterns,
 * making the intended consistency semantics visible at the type level.
 * <p>
 * {@link com.crablet.eventstore.AppendCondition} and
 * {@link com.crablet.eventstore.AppendConditionBuilder} are implementation
 * details; callers never need to construct them directly.
 *
 * <ul>
 *   <li>{@link Commutative}    — order-independent (deposits, credits)</li>
 *   <li>{@link NonCommutative} — stream-position-based DCB check (withdrawals, transfers)</li>
 *   <li>{@link Idempotent}     — entity creation; fails on duplicate (OpenWallet)</li>
 *   <li>{@link NoOp}           — no operation needed (already applied)</li>
 * </ul>
 */
public sealed interface CommandDecision
        permits CommandDecision.Commutative, CommandDecision.NonCommutative,
                CommandDecision.Idempotent, CommandDecision.NoOp {

    /** Commutative — order-independent; no conflict detection needed. */
    record Commutative(List<AppendEvent> events) implements CommandDecision {}

    /** Non-commutative — stream-position-based DCB conflict check. */
    record NonCommutative(List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition)
            implements CommandDecision {}

    /** Idempotent — entity creation; fails if an event with the same tag already exists. */
    record Idempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue)
            implements CommandDecision {}

    /**
     * No-op decision for handlers that detect the operation has already been applied.
     * {@link com.crablet.command.CommandExecutor} skips the append when this is returned.
     */
    record NoOp(String reason) implements CommandDecision {
        /** Convenience factory when no reason is needed. */
        public static NoOp empty() {
            return new NoOp(null);
        }

        /** @deprecated Use {@link #empty()} instead. */
        @Deprecated
        public static NoOp noReason() {
            return empty();
        }
    }

    /**
     * Returns the events associated with this decision, or an empty list for {@link NoOp}.
     * Convenience method for callers that only need the event list.
     */
    default List<AppendEvent> events() {
        return switch (this) {
            case Commutative c -> c.events();
            case NonCommutative nc -> nc.events();
            case Idempotent i -> i.events();
            case NoOp e -> List.of();
        };
    }
}
