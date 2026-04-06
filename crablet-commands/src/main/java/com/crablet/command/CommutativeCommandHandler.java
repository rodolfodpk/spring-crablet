package com.crablet.command;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;

import java.util.List;

/**
 * Specialization of {@link CommandHandler} for <b>commutative</b> operations —
 * those where event order does not affect the final business outcome
 * (e.g., deposits, credits, batch increments).
 * <p>
 * Implementors return only the events to append; the framework applies
 * {@link com.crablet.eventstore.dcb.AppendCondition#empty()} automatically,
 * allowing parallel appends without conflict detection.
 * <p>
 * Any business validation (e.g., existence checks) must be performed inside
 * {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface CommutativeCommandHandler<C> extends CommandHandler<C> {

    /**
     * Carries the events to append.
     *
     * @param events the events to append
     */
    record Decision(List<AppendEvent> events) {
        /** Single-event factory — the common case. */
        public static Decision of(AppendEvent event) {
            return new Decision(List.of(event));
        }
        /** Multi-event factory. */
        public static Decision of(AppendEvent... events) {
            return new Decision(List.of(events));
        }
    }

    /**
     * Handle the command and return the events to append.
     * No append condition is needed; the framework handles that automatically.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return the decision carrying the events to append
     */
    Decision decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        return new CommandDecision.Commutative(decide(eventStore, command).events());
    }
}
