package com.crablet.command;

import com.crablet.eventstore.EventStore;

/**
 * Specialization of {@link CommandHandler} for <b>non-commutative</b> operations —
 * those where event order matters for correctness
 * (e.g., withdrawals, transfers, capacity changes).
 * <p>
 * Implementors return a {@link CommandDecision.NonCommutative} from {@link #decide},
 * carrying the events plus the decision model and stream position captured during
 * projection; the framework applies a stream-position-based DCB concurrency check
 * automatically, failing the append if any concurrent modification is detected.
 * <p>
 * Use {@link CommandDecision.NonCommutative#of} for the common single-event case.
 * All business validation (existence, balance, constraints) must be performed inside
 * {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface NonCommutativeCommandHandler<C> extends CommandHandler<C> {

    /**
     * Handle the command and return a non-commutative decision carrying the events,
     * decision model, and stream position for the DCB conflict check.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return non-commutative decision carrying events, decision model, and stream position
     */
    CommandDecision.NonCommutative decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        return decide(eventStore, command);
    }
}
