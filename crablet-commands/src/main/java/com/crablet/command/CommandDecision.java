package com.crablet.command;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;

import java.util.List;

/**
 * Sealed decision type for command handling.
 * Each variant corresponds to one of the DCB concurrency patterns,
 * making the intended consistency semantics visible at the type level.
 * <p>
 * Command handlers implement one of the typed sub-interfaces
 * ({@link CommutativeCommandHandler}, {@link NonCommutativeCommandHandler},
 * {@link IdempotentCommandHandler}) and return the matching variant from
 * {@code decide()}. The {@link com.crablet.command.internal.CommandExecutorImpl}
 * pattern-matches on the variant to call the correct {@code EventStore} append method.
 *
 * <ul>
 *   <li>{@link Commutative}        — order-independent (deposits, credits)</li>
 *   <li>{@link CommutativeGuarded} — order-independent with lifecycle guard</li>
 *   <li>{@link NonCommutative}     — stream-position-based DCB check (withdrawals, transfers)</li>
 *   <li>{@link Idempotent}         — entity creation; fails on duplicate (OpenWallet)</li>
 *   <li>{@link NoOp}               — no operation needed (already applied)</li>
 * </ul>
 */
public sealed interface CommandDecision
        permits CommandDecision.CommutativeDecision, CommandDecision.NonCommutative,
                CommandDecision.Idempotent, CommandDecision.NoOp {

    /**
     * Marker type for the two commutative variants.
     * {@link CommutativeCommandHandler#decide()} returns this type, so the compiler
     * prevents accidentally returning a {@link NonCommutative} or {@link Idempotent} decision.
     */
    sealed interface CommutativeDecision extends CommandDecision
            permits CommandDecision.Commutative, CommandDecision.CommutativeGuarded {}

    /** Commutative — order-independent; no conflict detection needed. */
    record Commutative(List<AppendEvent> events) implements CommandDecision.CommutativeDecision {
        /** Single-event factory — the common case. */
        public static Commutative of(AppendEvent event) {
            return new Commutative(List.of(event));
        }
        /** Multi-event factory. */
        public static Commutative of(AppendEvent... events) {
            return new Commutative(List.of(events));
        }
    }

    /**
     * Commutative with selective lifecycle guard.
     * <p>
     * Like {@link Commutative}, parallel operations of the same type (e.g., concurrent deposits)
     * do not conflict. Additionally, the executor atomically checks whether any event matching
     * {@code lifecycleQuery} appeared <em>after</em> {@code guardPosition} before appending.
     * <p>
     * The guard detects lifecycle changes (e.g., {@code WalletClosed}) that would invalidate
     * the operation's precondition. If a conflict is detected the executor throws
     * {@link com.crablet.eventstore.ConcurrencyException} so the caller can retry.
     * <p>
     * <strong>Guard query design rule:</strong> the lifecycle query must include only lifecycle
     * event types (e.g., {@code WalletOpened}, {@code WalletClosed}) — NOT the commutative
     * event types (e.g., {@code DepositMade}) — so that concurrent commutative operations
     * do not trigger spurious conflicts.
     */
    record CommutativeGuarded(
            List<AppendEvent> events,
            Query guardQuery,
            StreamPosition guardPosition
    ) implements CommandDecision.CommutativeDecision {
        /**
         * Factory for commutative operations with a lifecycle guard.
         * Use this to allow concurrent operations of the same type (e.g., deposits) while
         * still detecting lifecycle changes (e.g., wallet closing) that would invalidate
         * the operation's precondition.
         *
         * @param event         the event to append
         * @param lifecycleQuery query scoped to lifecycle event types only (e.g., WalletOpened, WalletClosed)
         * @param guardPosition  the stream position captured during the handler's projection
         */
        public static CommutativeGuarded withLifecycleGuard(
                AppendEvent event, Query lifecycleQuery, StreamPosition guardPosition) {
            return new CommutativeGuarded(List.of(event), lifecycleQuery, guardPosition);
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
            case CommutativeGuarded cg -> cg.events();
            case NonCommutative nc -> nc.events();
            case Idempotent i -> i.events();
            case NoOp e -> List.of();
        };
    }
}
