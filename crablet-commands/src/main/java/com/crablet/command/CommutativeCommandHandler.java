package com.crablet.command;

import com.crablet.eventstore.EventStore;

/**
 * Specialization of {@link CommandHandler} for <b>commutative</b> operations —
 * those where event order does not affect the final business outcome
 * (e.g., deposits, credits, batch increments).
 * <p>
 * Implementors return a {@link CommandDecision.Commutative} from {@link #decide};
 * the framework calls {@code EventStore.appendCommutative} automatically,
 * allowing parallel appends without conflict detection.
 * <p>
 * Any business validation (e.g., existence checks) must be performed inside
 * {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface CommutativeCommandHandler<C> extends CommandHandler<C> {

    /**
     * Handle the command and return a commutative decision carrying the events to append.
     * Use {@link CommandDecision.Commutative#of(com.crablet.eventstore.AppendEvent)} for
     * the common single-event case.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return commutative decision carrying the events to append
     */
    CommandDecision.Commutative decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        return decide(eventStore, command);
    }
}
