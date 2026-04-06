package com.crablet.command;

import com.crablet.eventstore.EventStore;

/**
 * Specialization of {@link CommandHandler} for <b>idempotent</b> operations —
 * entity creation where the operation must succeed exactly once per unique identifier
 * (e.g., OpenWallet, DefineCourse, SendWelcomeNotification).
 * <p>
 * Implementors return a {@link CommandDecision.Idempotent} from {@link #decide},
 * carrying the events together with the idempotency tag; the framework applies a
 * DCB idempotency check automatically, failing the append if an event with the same
 * tag already exists.
 * <p>
 * Use {@link CommandDecision.Idempotent#of} for the common single-event case.
 * All business logic must be performed inside {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface IdempotentCommandHandler<C> extends CommandHandler<C> {

    /**
     * Handle the command and return an idempotent decision carrying the events and
     * the idempotency tag information.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return idempotent decision carrying events and idempotency tag
     */
    CommandDecision.Idempotent decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        return decide(eventStore, command);
    }
}
