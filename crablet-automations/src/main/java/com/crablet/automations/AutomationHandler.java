package com.crablet.automations;

import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.processor.ProcessorRuntimeOverrides;
import com.crablet.eventstore.StoredEvent;

import java.util.Map;
import java.util.Set;

/**
 * Automation handler. Implement and register as a Spring {@code @Component}.
 *
 * <p>Handlers execute in-process by default. They may also opt into webhook delivery by
 * returning a non-blank value from {@link #getWebhookUrl()}. This keeps matching,
 * delivery, and per-automation runtime tuning on a single contract.
 *
 * <p>Use this interface when the handler lives in the same JVM as the event poller
 * (e.g., dispatching a follow-up command via {@link CommandExecutor}, triggering a
 * notification via an injected client). Returning a webhook URL switches delivery to HTTP
 * for the same handler definition.
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
 * <p>In-process execution is preferred for internal command dispatch within the same
 * deployment unit. Webhook delivery remains available for external integrations.
 */
public interface AutomationHandler extends AutomationDefinition, ProcessorRuntimeOverrides {

    /** Unique name identifying this automation. */
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

    /** Optional webhook target. Non-blank value switches delivery from in-process to HTTP POST. */
    default String getWebhookUrl() { return null; }

    /** Static HTTP headers to include in webhook requests. */
    default Map<String, String> getWebhookHeaders() { return Map.of(); }

    /** Per-request timeout in milliseconds for webhook delivery. */
    default int getWebhookTimeoutMs() { return 5000; }

    /** Override polling interval for this automation only (ms). Null = use global default. */
    default Long getPollingIntervalMs() { return null; }

    /** Override batch size for this automation only. Null = use global default. */
    default Integer getBatchSize() { return null; }

    /** Override backoff enabled for this automation only. Null = use global default (true). */
    default Boolean getBackoffEnabled() { return null; }

    /** Override backoff threshold for this automation only. Null = use global default. */
    default Integer getBackoffThreshold() { return null; }

    /** Override backoff multiplier for this automation only. Null = use global default. */
    default Integer getBackoffMultiplier() { return null; }

    /** Override max backoff seconds for this automation only. Null = use global default. */
    default Integer getBackoffMaxSeconds() { return null; }

    /**
     * Called once per matching event for in-process execution.
     *
     * @param event           the matching stored event
     * @param commandExecutor executor for dispatching follow-up commands
     */
    default void react(StoredEvent event, CommandExecutor commandExecutor) {
        throw new UnsupportedOperationException(
                "Automation " + getAutomationName() + " is configured for webhook delivery only");
    }
}
