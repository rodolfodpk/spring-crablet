package com.crablet.command;

import com.crablet.eventstore.EventStore;

/**
 * Generic interface for type-safe command handling.
 * <p>
 * Handlers project state from the {@link EventStore}, validate business rules,
 * and return a {@link CommandDecision} describing the intended concurrency
 * semantics and events to append.
 */
public interface CommandHandler<T> {

    /**
     * Handle a command following DCB pattern.
     * <p>
     * Typical flow:
     * 1. Project the decision model from the event store
     * 2. Validate business rules from the projected state
     * 3. Create events
     * 4. Return the appropriate {@link CommandDecision} variant
     * <p>
     * {@link CommandExecutor} atomically interprets the returned decision and
     * calls the matching {@link EventStore} append method.
     *
     * @param eventStore The event store for projections
     * @param command    The command to handle
     * @return the command decision describing how the events should be appended
     */
    CommandDecision handle(EventStore eventStore, T command);
}
