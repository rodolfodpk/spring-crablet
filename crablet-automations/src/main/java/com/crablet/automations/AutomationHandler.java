package com.crablet.automations;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.store.StoredEvent;

/**
 * Core interface for event-driven automations (policies/sagas).
 * An automation handler listens to specific domain events and responds by executing
 * one or more commands — the "when X happens, do Y" pattern from event storming.
 */
public interface AutomationHandler {

    /**
     * Unique name identifying this automation. Must match the name in the corresponding
     * {@link AutomationSubscription}.
     */
    String getAutomationName();

    /**
     * React to a single domain event by executing one or more commands.
     *
     * @param event           the event that triggered this automation
     * @param commandExecutor executor for issuing commands
     */
    void react(StoredEvent event, CommandExecutor commandExecutor);
}
