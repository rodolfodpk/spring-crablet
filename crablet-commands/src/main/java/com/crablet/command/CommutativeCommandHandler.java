package com.crablet.command;

import com.crablet.eventstore.EventStore;

/**
 * Specialization of {@link CommandHandler} for <b>commutative</b> operations —
 * those where event order does not affect the final business outcome
 * (e.g., deposits, credits, batch increments).
 * <p>
 * Implementors return either a {@link CommandDecision.Commutative} or a
 * {@link CommandDecision.CommutativeGuarded} from {@link #decide}:
 * <ul>
 *   <li>{@code Commutative.of(event)} — no conflict detection; allows fully parallel appends</li>
 *   <li>{@code CommutativeGuarded.withLifecycleGuard(event, lifecycleQuery, guardPosition)} — parallel
 *       for same-type operations, but atomically checks that no lifecycle event (e.g., WalletClosed)
 *       appeared after the captured stream position</li>
 * </ul>
 * <p>
 * Any business validation (e.g., existence checks) must be performed inside
 * {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface CommutativeCommandHandler<C> extends CommandHandler<C> {

    /**
     * Handle the command and return a commutative decision carrying the events to append.
     * <p>
     * Return {@link CommandDecision.Commutative#of(com.crablet.eventstore.AppendEvent)} for
     * the common case, or
     * {@link CommandDecision.CommutativeGuarded#withLifecycleGuard(com.crablet.eventstore.AppendEvent,
     * com.crablet.eventstore.query.Query, com.crablet.eventstore.StreamPosition)}
     * when a lifecycle guard is needed.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return {@link CommandDecision.Commutative} or {@link CommandDecision.CommutativeGuarded}
     */
    CommandDecision.CommutativeDecision decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        return decide(eventStore, command);
    }
}
