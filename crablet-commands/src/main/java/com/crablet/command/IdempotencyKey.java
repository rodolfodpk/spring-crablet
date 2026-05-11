package com.crablet.command;

import com.crablet.eventstore.Stable;

/**
 * Idempotency specification carried by a {@link CommandDecision} variant.
 * <p>
 * Identifies a prior event by type and tag so the event store can detect duplicate
 * appends. Use stable business keys (e.g., {@code deposit_id}, {@code withdrawal_id})
 * as tag values — not random transaction IDs or event positions.
 *
 * <ul>
 *   <li>{@link OnDuplicate#RETURN_IDEMPOTENT} (default) — silently return success when a duplicate is detected.</li>
 *   <li>{@link OnDuplicate#THROW} — raise {@link com.crablet.eventstore.ConcurrencyException} for uniqueness-only flows.</li>
 * </ul>
 *
 * @param eventType  event type name used for the duplicate check (must not be blank)
 * @param tagKey     tag key identifying the unique operation (must not be blank)
 * @param tagValue   tag value identifying the specific operation instance (must not be blank)
 * @param onDuplicate policy controlling what happens when a duplicate is detected
 */
@Stable
public record IdempotencyKey(
        String eventType,
        String tagKey,
        String tagValue,
        OnDuplicate onDuplicate
) {
    public IdempotencyKey {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey eventType must not be blank");
        }
        if (tagKey == null || tagKey.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey tagKey must not be blank");
        }
        if (tagValue == null || tagValue.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey tagValue must not be blank");
        }
        if (onDuplicate == null) {
            throw new IllegalArgumentException("IdempotencyKey onDuplicate must not be null");
        }
    }

    /** Factory — defaults to {@link OnDuplicate#RETURN_IDEMPOTENT}. */
    public static IdempotencyKey of(String eventType, String tagKey, String tagValue) {
        return new IdempotencyKey(eventType, tagKey, tagValue, OnDuplicate.RETURN_IDEMPOTENT);
    }

    /** Factory with explicit duplicate policy. */
    public static IdempotencyKey of(String eventType, String tagKey, String tagValue, OnDuplicate onDuplicate) {
        return new IdempotencyKey(eventType, tagKey, tagValue, onDuplicate);
    }
}
