package com.crablet.eventstore.command;

import com.crablet.eventstore.store.EventStore;

/**
 * CommandHandler handles command execution and generates events.
 * Based on the Go implementation's CommandHandler interface.
 * <p>
 * Generic interface for type-safe command handling with self-identification.
 */
public interface CommandHandler<T> {

    /**
     * Handle a command following DCB pattern.
     * <p>
     * DCB Flow:
     * 1. Project decision model (read events via tags)
     * 2. Validate business rules from state
     * 3. Create events
     * 4. Build AppendCondition from projection
     * 5. Return events + condition
     * <p>
     * CommandExecutor will atomically append events with condition.
     *
     * @param eventStore The event store for projections
     * @param command    The command to handle
     * @return CommandResult with events and append condition
     */
    CommandResult handle(EventStore eventStore, T command);
}
