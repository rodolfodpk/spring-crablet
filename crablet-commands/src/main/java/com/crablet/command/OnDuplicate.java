package com.crablet.command;

/**
 * Policy controlling what happens when an idempotent append detects a duplicate.
 * <ul>
 *   <li>{@link #THROW} — throw {@link com.crablet.eventstore.ConcurrencyException};
 *       use for entity-creation commands where a duplicate indicates a conflict
 *       (e.g., {@code OpenWalletCommand}).</li>
 *   <li>{@link #RETURN_IDEMPOTENT} — return a successful idempotent result silently;
 *       use for operations that are naturally idempotent (e.g., re-sent notifications).</li>
 * </ul>
 */
public enum OnDuplicate {
    THROW,
    RETURN_IDEMPOTENT
}
