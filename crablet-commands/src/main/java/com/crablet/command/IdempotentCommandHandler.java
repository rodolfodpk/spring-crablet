package com.crablet.command;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;

import java.util.List;

/**
 * Specialization of {@link CommandHandler} for <b>idempotent</b> operations —
 * entity creation where the operation must succeed exactly once per unique identifier
 * (e.g., OpenWallet, DefineCourse, SendWelcomeNotification).
 * <p>
 * Implementors return the events together with the idempotency tag that uniquely
 * identifies the operation; the framework applies a DCB idempotency check automatically,
 * failing the append if an event with the same tag already exists.
 * <p>
 * All business logic must be performed inside {@link #decide} before returning.
 *
 * @param <C> the command type
 */
public interface IdempotentCommandHandler<C> extends CommandHandler<C> {

    /**
     * Carries the events to append together with the idempotency tag information.
     *
     * @param events     the events to append
     * @param eventType  the event type name used for the idempotency check
     * @param tagKey     the tag key used for the idempotency check
     * @param tagValue   the tag value used for the idempotency check
     */
    record Decision(List<AppendEvent> events, String eventType, String tagKey, String tagValue,
                    OnDuplicate onDuplicate) {
        /** Single-event factory — defaults to {@link OnDuplicate#RETURN_IDEMPOTENT}. */
        public static Decision of(AppendEvent event, String eventType, String tagKey, String tagValue) {
            return new Decision(List.of(event), eventType, tagKey, tagValue, OnDuplicate.RETURN_IDEMPOTENT);
        }

        /**
         * Single-event factory with explicit duplicate policy.
         * Use {@link OnDuplicate#THROW} for entity-creation commands that must be unique
         * (e.g., {@code OpenWalletCommand}).
         */
        public static Decision of(AppendEvent event, String eventType, String tagKey, String tagValue,
                                  OnDuplicate onDuplicate) {
            return new Decision(List.of(event), eventType, tagKey, tagValue, onDuplicate);
        }
    }

    /**
     * Handle the command and return the events plus idempotency tag information.
     *
     * @param eventStore the event store for projections
     * @param command    the command to handle
     * @return the decision carrying events and idempotency tag
     */
    Decision decide(EventStore eventStore, C command);

    @Override
    default CommandDecision handle(EventStore eventStore, C command) {
        Decision d = decide(eventStore, command);
        return new CommandDecision.Idempotent(d.events(), d.eventType(), d.tagKey(), d.tagValue(),
                d.onDuplicate());
    }
}
