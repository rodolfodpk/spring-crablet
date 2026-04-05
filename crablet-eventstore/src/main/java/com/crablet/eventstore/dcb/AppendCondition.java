package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.StreamPosition;
import org.jspecify.annotations.Nullable;

/**
 * AppendCondition defines the conditions for appending events.
 * Supports two independent checks:
 * - concurrencyQuery: detects conflicting writes after a captured stream position
 * - idempotencyQuery: detects duplicate operations regardless of position (Query.empty() = no check)
 */
public record AppendCondition(
        StreamPosition afterPosition,
        Query concurrencyQuery,
        Query idempotencyQuery
) {

    /**
     * Low-level factory — concurrency check only, no idempotency check.
     * Prefer {@link AppendConditionBuilder} for constructing conditions in command handlers.
     */
    public static AppendCondition of(@Nullable StreamPosition afterPosition, @Nullable Query concurrencyQuery) {
        if (afterPosition == null) {
            throw new IllegalArgumentException("afterPosition cannot be null");
        }
        if (concurrencyQuery == null) {
            throw new IllegalArgumentException("concurrencyQuery cannot be null");
        }
        return new AppendCondition(afterPosition, concurrencyQuery, Query.empty());
    }

    /**
     * Low-level factory — concurrency check with optional idempotency check.
     * Prefer {@link AppendConditionBuilder} for constructing conditions in command handlers.
     */
    public static AppendCondition of(@Nullable StreamPosition afterPosition, @Nullable Query concurrencyQuery, @Nullable Query idempotencyQuery) {
        if (afterPosition == null) {
            throw new IllegalArgumentException("afterPosition cannot be null");
        }
        if (concurrencyQuery == null) {
            throw new IllegalArgumentException("concurrencyQuery cannot be null");
        }
        return new AppendCondition(afterPosition, concurrencyQuery, idempotencyQuery != null ? idempotencyQuery : Query.empty());
    }

    /**
     * Low-level factory — stream position check against an empty query (passes unconditionally).
     * Prefer {@link AppendConditionBuilder} for constructing conditions in command handlers.
     */
    public static AppendCondition of(StreamPosition afterPosition) {
        return of(afterPosition, Query.empty());
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
        return AppendConditionBuilder.of(Query.empty(), StreamPosition.zero())
                .withIdempotencyCheck(eventType, tagKey, tagValue)
                .build();
    }

    /**
     * Create an empty condition for operations that don't need DCB checks.
     * Use for commutative operations where order doesn't matter (e.g., deposits),
     * or for new stream creation when duplicate protection is handled via
     * {@link com.crablet.eventstore.dcb.AppendConditionBuilder#withIdempotencyCheck}.
     * These operations can safely run in parallel without conflicts.
     */
    public static AppendCondition empty() {
        return new AppendCondition(StreamPosition.zero(), Query.empty(), Query.empty());
    }
}
