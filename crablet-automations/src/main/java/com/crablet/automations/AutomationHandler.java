package com.crablet.automations;

import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.processor.ProcessorRuntimeOverrides;
import com.crablet.eventstore.StoredEvent;

import java.util.Set;

/**
 * Automation handler. Implement and register as a Spring {@code @Component}.
 *
 * <p>Use this interface when the handler lives in the same JVM as the event poller
 * (e.g., dispatching a follow-up command via {@link CommandExecutor}, triggering a
 * notification via an injected client). Automations are for application reaction and
 * orchestration. Use {@code OutboxPublisher} when stored events need to be published to
 * external systems such as HTTP webhooks, Kafka, analytics, or CRM integrations.
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
 * <p>Each matching event is delivered to {@link #react(StoredEvent, CommandExecutor)}.
 * The automation should make application decisions and record outcomes through commands
 * and events where appropriate.
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
    void react(StoredEvent event, CommandExecutor commandExecutor);
}
