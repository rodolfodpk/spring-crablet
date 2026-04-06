package com.crablet.command;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.EventStore;

import java.util.List;

/**
 * Specialization of {@link CommandHandler} for <b>non-commutative</b> operations —
 * those where event order matters for correctness
 * (e.g., withdrawals, transfers, capacity changes).
 * <p>
 * Implementors return the events plus the decision model and stream position captured
 * during projection; the framework applies a stream-position-based DCB concurrency check
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
     * and the stream position captured from the projection result.
     *
     * @param events        the events to append
     * @param decisionModel the query used to build the decision model (same query used for projection)
     * @param streamPosition        the stream position captured from the projection result
     */
    record Decision(List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition) {
        /** Single-event factory — the common case. */
        public static Decision of(AppendEvent event, Query decisionModel, StreamPosition streamPosition) {
            return new Decision(List.of(event), decisionModel, streamPosition);
        }
    }

    /**
     * Handle the command and return the events plus DCB concurrency inputs.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return the decision carrying events, decision model and stream position
     */
    Decision decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        Decision d = decide(eventStore, command);
        return new CommandDecision.NonCommutative(d.events(), d.decisionModel(), d.streamPosition());
    }
}
