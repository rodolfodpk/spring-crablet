package com.crablet.command;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import java.util.List;

/**
 * Result of command handling containing events and append condition.
 * Following DCB pattern: handlers build decision model, create events,
 * and return condition for atomic constraint enforcement.
 * <p>
 * The reason field provides context when operation is idempotent (empty events).
 */
public record CommandResult(
        List<AppendEvent> events,
        AppendCondition appendCondition,
        String reason  // Reason for idempotency (null when operation creates events)
) {
    public static CommandResult of(List<AppendEvent> events, AppendCondition condition) {
        return new CommandResult(events, condition, null);
    }

    public static CommandResult empty() {
        return new CommandResult(List.of(), AppendCondition.expectEmptyStream(), null);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}

