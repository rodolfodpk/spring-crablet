package com.crablet.automations;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;

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

    /**
     * Build the subscription for this automation. Derives the automation name from
     * {@link #getAutomationName()}, making name mismatches between handler and
     * subscription impossible.
     *
     * <pre>{@code
     * @Bean
     * AutomationSubscription walletOpenedSubscription(WalletOpenedReaction handler) {
     *     return handler.subscription(type(WalletOpened.class));
     * }
     * }</pre>
     *
     * @param eventTypes event type names that trigger this automation
     */
    default AutomationSubscription subscription(String... eventTypes) {
        return AutomationSubscription.builder(getAutomationName())
                .eventTypes(eventTypes)
                .build();
    }
}
