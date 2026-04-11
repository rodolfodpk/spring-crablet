package com.crablet.automations;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;

import java.util.Set;

/**
 * In-process automation handler. Implement and register as a Spring {@code @Component}.
 * Called directly by the automation processor — no HTTP involved.
 *
 * <p>Use this interface when the handler lives in the same JVM as the event poller
 * (e.g., dispatching a follow-up command via {@link CommandExecutor}, triggering a
 * notification via an injected client). Avoids the HTTP round-trip of webhook automations.
 *
 * <pre>{@code
 * @Component
 * public class WalletOpenedHandler implements AutomationHandler {
 *
 *     private final ObjectMapper objectMapper;
 *
 *     public WalletOpenedHandler(ObjectMapper objectMapper) {
 *         this.objectMapper = objectMapper;
 *     }
 *
 *     @Override
 *     public String getAutomationName() { return "wallet-opened-welcome"; }
 *
 *     @Override
 *     public Set<String> getEventTypes() {
 *         return Set.of(type(WalletOpened.class));
 *     }
 *
 *     @Override
 *     public void react(StoredEvent event, CommandExecutor commandExecutor) {
 *         WalletOpened opened = objectMapper.readValue(event.data(), WalletOpened.class);
 *         commandExecutor.execute(SendWelcomeNotificationCommand.of(opened.walletId(), opened.owner()));
 *     }
 * }
 * }</pre>
 *
 * <p>Webhooks remain for external integrations. In-process handlers are preferred for
 * internal command dispatch within the same deployment unit.
 */
public interface AutomationHandler extends AutomationDefinition {

    /** Unique name identifying this automation. Must match no webhook {@link AutomationSubscription}. */
    @Override
    String getAutomationName();

    /** Event types that trigger this handler. Empty set means all events (use with care). */
    @Override
    Set<String> getEventTypes();

    /** ALL of these tag keys must be present on the event to trigger this handler. */
    @Override
    default Set<String> getRequiredTags() { return Set.of(); }

    /** At least ONE of these tag keys must be present on the event to trigger this handler. */
    @Override
    default Set<String> getAnyOfTags() { return Set.of(); }

    /**
     * Called once per matching event.
     *
     * @param event           the matching stored event
     * @param commandExecutor executor for dispatching follow-up commands
     */
    void react(StoredEvent event, CommandExecutor commandExecutor);
}
