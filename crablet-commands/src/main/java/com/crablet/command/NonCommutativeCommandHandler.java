package com.crablet.command;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;

import java.util.List;

/**
 * Specialization of {@link CommandHandler} for <b>non-commutative</b> operations —
 * those where event order matters for correctness
 * (e.g., withdrawals, transfers, capacity changes).
 * <p>
 * Implementors return the events plus the decision model and cursor captured
 * during projection; the framework applies a cursor-based DCB concurrency check
 * automatically, failing the append if any concurrent modification is detected.
 * <p>
 * All business validation (existence, balance, constraints) must be performed inside
 * {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface NonCommutativeCommandHandler<C> extends CommandHandler<C> {

    /**
     * Carries the events to append together with the DCB decision model
     * and the cursor captured from the projection result.
     *
     * @param events        the events to append
     * @param decisionModel the query used to build the decision model (same query used for projection)
     * @param cursor        the cursor captured from the projection result
     */
    record Decision(List<AppendEvent> events, Query decisionModel, Cursor cursor) {}

    /**
     * Handle the command and return the events plus DCB concurrency inputs.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return the decision carrying events, decision model and cursor
     */
    Decision decide(EventStore eventStore, C command);

    @Override
    default CommandResult handle(EventStore eventStore, C command) {
        Decision d = decide(eventStore, command);
        return CommandResult.nonCommutative(d.events(), d.decisionModel(), d.cursor());
    }
}
