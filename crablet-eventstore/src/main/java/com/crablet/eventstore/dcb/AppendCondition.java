package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.Cursor;
import org.jspecify.annotations.Nullable;

/**
 * AppendCondition defines the conditions for appending events.
 * Supports dual conditions: concurrency check (with cursor) and idempotency check (no cursor).
 * This follows the DCB specification where idempotency checks ignore cursor position.
 */
public record AppendCondition(
        Cursor afterCursor,
        Query stateChanged,          // Concurrency check (with cursor)
        @Nullable Query alreadyExists // Idempotency check (no cursor)
) {

    /**
     * Low-level factory — concurrency check only, no idempotency check.
     * Prefer {@link AppendConditionBuilder} for constructing conditions in command handlers.
     */
    public static AppendCondition of(@Nullable Cursor afterCursor, @Nullable Query stateChanged) {
        if (afterCursor == null) {
            throw new IllegalArgumentException("afterCursor cannot be null");
        }
        if (stateChanged == null) {
            throw new IllegalArgumentException("stateChanged cannot be null");
        }
        return new AppendCondition(afterCursor, stateChanged, null);
    }

    /**
     * Low-level factory — concurrency check with optional idempotency check.
     * Prefer {@link AppendConditionBuilder} for constructing conditions in command handlers.
     */
    public static AppendCondition of(@Nullable Cursor afterCursor, @Nullable Query stateChangedQuery, @Nullable Query alreadyExistsQuery) {
        if (afterCursor == null) {
            throw new IllegalArgumentException("afterCursor cannot be null");
        }
        if (stateChangedQuery == null) {
            throw new IllegalArgumentException("stateChangedQuery cannot be null");
        }
        return new AppendCondition(afterCursor, stateChangedQuery, alreadyExistsQuery);
    }

    /**
     * Low-level factory — cursor check against an empty query (passes unconditionally).
     * Prefer {@link AppendConditionBuilder} for constructing conditions in command handlers.
     */
    public static AppendCondition of(Cursor afterCursor) {
        return of(afterCursor, Query.empty());
    }

    /**
     * Create an idempotency-only condition for entity creation commands.
     * Fails if an event of {@code eventType} tagged with {@code tagKey=tagValue} already exists.
     * <p>
     * Replaces the verbose chain:
     * <pre>{@code
     * AppendConditionBuilder.of(Query.empty(), Cursor.zero())
     *     .withIdempotencyCheck(type(WalletOpened.class), WALLET_ID, walletId)
     *     .build();
     * }</pre>
     * with:
     * <pre>{@code
     * AppendCondition.idempotent(type(WalletOpened.class), WALLET_ID, walletId);
     * }</pre>
     */
    public static AppendCondition idempotent(String eventType, String tagKey, String tagValue) {
        return AppendConditionBuilder.of(Query.empty(), Cursor.zero())
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
        return new AppendCondition(Cursor.zero(), Query.empty(), null);
    }
}
