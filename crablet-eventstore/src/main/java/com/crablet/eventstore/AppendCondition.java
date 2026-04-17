package com.crablet.eventstore;

import com.crablet.eventstore.query.Query;
import org.jspecify.annotations.Nullable;

/**
 * Advanced low-level append condition for direct {@link EventStore} usage.
 * <p>
 * Most applications should prefer the semantic append methods on
 * {@link EventStore} ({@code appendCommutative}, {@code appendNonCommutative},
 * {@code appendIdempotent}) or, in the command framework, return
 * {@code CommandDecision} variants from handlers.
 * <p>
 * This type remains public for advanced direct-{@code EventStore} scenarios
 * that need to compose concurrency and idempotency checks explicitly.
 * It supports two independent checks:
 * <ul>
 *   <li>{@code concurrencyQuery}: detects conflicting writes after a captured stream position</li>
 *   <li>{@code idempotencyQuery}: detects duplicate operations regardless of position</li>
 * </ul>
 */
public record AppendCondition(
        StreamPosition afterPosition,
        Query concurrencyQuery,
        Query idempotencyQuery
) {

    /**
     * Low-level factory — concurrency check only, no idempotency check.
     * Intended for advanced direct-{@link EventStore} usage, not command handlers.
     */
    public static AppendCondition of(@Nullable StreamPosition afterPosition, @Nullable Query concurrencyQuery) {
        if (afterPosition == null) {
            throw new IllegalArgumentException("afterPosition cannot be null");
        }
        if (concurrencyQuery == null) {
            throw new IllegalArgumentException("concurrencyQuery cannot be null");
        }
        return new AppendCondition(afterPosition, concurrencyQuery, Query.noCondition());
    }

    /**
     * Low-level factory — concurrency check with optional idempotency check.
     * Intended for advanced direct-{@link EventStore} usage, not command handlers.
     */
    public static AppendCondition of(@Nullable StreamPosition afterPosition, @Nullable Query concurrencyQuery, @Nullable Query idempotencyQuery) {
        if (afterPosition == null) {
            throw new IllegalArgumentException("afterPosition cannot be null");
        }
        if (concurrencyQuery == null) {
            throw new IllegalArgumentException("concurrencyQuery cannot be null");
        }
        return new AppendCondition(afterPosition, concurrencyQuery, idempotencyQuery != null ? idempotencyQuery : Query.noCondition());
    }

    /**
     * Low-level factory — stream position check against an empty query (passes unconditionally).
     * Intended for advanced direct-{@link EventStore} usage, not command handlers.
     */
    public static AppendCondition of(StreamPosition afterPosition) {
        return of(afterPosition, Query.noCondition());
    }

    /**
     * Create an idempotency-only condition for entity creation commands.
     * Fails if an event of {@code eventType} tagged with {@code tagKey=tagValue} already exists.
     * <p>
     * Replaces the verbose chain:
     * <pre>{@code
     * AppendConditionBuilder.of(Query.empty(), StreamPosition.zero())
     *     .withIdempotencyCheck(type(WalletOpened.class), WALLET_ID, walletId)
     *     .build();
     * }</pre>
     * with:
     * <pre>{@code
     * AppendCondition.idempotent(type(WalletOpened.class), WALLET_ID, walletId);
     * }</pre>
     */
    public static AppendCondition idempotent(String eventType, String tagKey, String tagValue) {
        return AppendConditionBuilder.of(Query.noCondition(), StreamPosition.zero())
                .withIdempotencyCheck(eventType, tagKey, tagValue)
                .build();
    }

    /**
     * Create an idempotency-only condition from a pre-built {@link Query}.
     * Prefer this overload when the idempotency criteria involve multiple event types or tags.
     * <p>
     * Equivalent to:
     * <pre>{@code
     * AppendCondition.of(StreamPosition.zero(), Query.noCondition(), idempotencyQuery);
     * }</pre>
     */
    public static AppendCondition idempotent(Query idempotencyQuery) {
        return new AppendCondition(StreamPosition.zero(), Query.noCondition(), idempotencyQuery);
    }

    /**
     * Create an empty condition for operations that don't need DCB checks.
     * Use for commutative operations where order doesn't matter (e.g., deposits),
     * or for new stream creation when duplicate protection is handled via
     * {@link AppendConditionBuilder#withIdempotencyCheck}.
     * These operations can safely run in parallel without conflicts.
     * <p>
     * Most callers should prefer {@link EventStore#appendCommutative(java.util.List)}
     * instead of constructing this directly.
     */
    public static AppendCondition empty() {
        return new AppendCondition(StreamPosition.zero(), Query.noCondition(), Query.noCondition());
    }
}
