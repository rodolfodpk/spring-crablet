package com.crablet.command;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Sealed decision type for command handling.
 * Each variant corresponds to one of the three DCB concurrency patterns,
 * making the intended consistency semantics visible at the type level.
 * <p>
 * Command handlers implement one of the three typed sub-interfaces
 * ({@link CommutativeCommandHandler}, {@link NonCommutativeCommandHandler},
 * {@link IdempotentCommandHandler}) and return the matching variant from
 * {@code decide()}. The {@link com.crablet.command.internal.CommandExecutorImpl}
 * pattern-matches on the variant to call the correct {@code EventStore} append method.
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

    /**
     * Commutative — order-independent; no full DCB conflict check needed.
     * <p>
     * Optionally carries a {@link Guard} that applies a selective DCB check on a narrower
     * lifecycle query before appending. The guard detects lifecycle state changes
     * (e.g., WalletClosed) without blocking concurrent commutative operations
     * (e.g., DepositMade is not in the guard query, so parallel deposits still succeed).
     */
    record Commutative(List<AppendEvent> events, @Nullable Guard guard)
            implements CommandDecision {

        /**
         * Selective DCB guard for commutative operations.
         * Carries a narrow lifecycle query and the stream position captured during projection.
         * The executor checks whether any event matching {@code query} appeared after
         * {@code streamPosition}; if so, it throws {@link com.crablet.eventstore.ConcurrencyException}
         * so the caller can retry and re-project the current state.
         */
        public record Guard(Query query, StreamPosition streamPosition) {}

        /** Single-event factory — no guard, current behavior. */
        public static Commutative of(AppendEvent event) {
            return new Commutative(List.of(event), null);
        }
        /** Multi-event factory — no guard. */
        public static Commutative of(AppendEvent... events) {
            return new Commutative(List.of(events), null);
        }

        /**
         * Single-event factory with selective lifecycle guard.
         * <p>
         * Use when the commutative operation has a lifecycle precondition that must hold atomically:
         * the {@code guardQuery} should include only lifecycle event types (e.g., WalletOpened,
         * WalletClosed) — NOT the commutative event types (e.g., DepositMade) — so that
         * concurrent commutative operations do not cause spurious conflicts.
         *
         * @param event          the event to append
         * @param guardQuery     narrow lifecycle query (must not include commutative event types)
         * @param guardPosition  stream position captured during projection
         */
        public static Commutative of(AppendEvent event, Query guardQuery, StreamPosition guardPosition) {
            return new Commutative(List.of(event), new Guard(guardQuery, guardPosition));
        }
    }

    /** Non-commutative — stream-position-based DCB conflict check. */
    record NonCommutative(List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition)
            implements CommandDecision {
        /** Single-event factory — the common case. */
        public static NonCommutative of(AppendEvent event, Query decisionModel, StreamPosition streamPosition) {
            return new NonCommutative(List.of(event), decisionModel, streamPosition);
        }
    }

    /**
     * Idempotent — entity creation; fails if an event with the same tag already exists.
     * {@code onDuplicate} controls whether a detected duplicate throws or returns silently.
     * Defaults to {@link OnDuplicate#RETURN_IDEMPOTENT} for backward compatibility.
     */
    record Idempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue,
                      OnDuplicate onDuplicate)
            implements CommandDecision {
        /** Backward-compatible constructor — defaults to RETURN_IDEMPOTENT. */
        public Idempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue) {
            this(events, eventType, tagKey, tagValue, OnDuplicate.RETURN_IDEMPOTENT);
        }

        /** Single-event factory — defaults to {@link OnDuplicate#RETURN_IDEMPOTENT}. */
        public static Idempotent of(AppendEvent event, String eventType, String tagKey, String tagValue) {
            return new Idempotent(List.of(event), eventType, tagKey, tagValue, OnDuplicate.RETURN_IDEMPOTENT);
        }

        /**
         * Single-event factory with explicit duplicate policy.
         * Use {@link OnDuplicate#THROW} for entity-creation commands that must be unique.
         */
        public static Idempotent of(AppendEvent event, String eventType, String tagKey, String tagValue,
                                    OnDuplicate onDuplicate) {
            return new Idempotent(List.of(event), eventType, tagKey, tagValue, onDuplicate);
        }
    }

    /**
     * No-op decision for handlers that detect the operation has already been applied.
     * {@link com.crablet.command.internal.CommandExecutorImpl} skips the append when this is returned.
     */
    record NoOp(String reason) implements CommandDecision {
        /** Convenience factory when no reason is needed. */
        public static NoOp empty() {
            return new NoOp(null);
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
