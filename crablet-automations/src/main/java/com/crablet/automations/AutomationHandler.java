package com.crablet.automations;

import com.crablet.eventpoller.processor.ProcessorRuntimeOverrides;
import com.crablet.eventstore.StoredEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Automation handler. Implement and register as a Spring {@code @Component}.
 *
 * <p>Use this interface when the handler lives in the same JVM as the event poller.
 * Automations are for application reaction and orchestration: handlers return
 * {@link AutomationDecision decisions}, and the dispatcher executes them. Use
 * {@code OutboxPublisher} when stored events need to be published to external systems
 * such as HTTP webhooks, Kafka, analytics, or CRM integrations.
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
 *     public List<AutomationDecision> decide(StoredEvent event) {
 *         WalletOpened opened = objectMapper.readValue(event.data(), WalletOpened.class);
 *         return List.of(new AutomationDecision.ExecuteCommand(
 *                 SendWelcomeNotificationCommand.of(opened.walletId(), opened.owner())));
 *     }
 * }
 * }</pre>
 *
 * <p>Each matching event is delivered to {@link #decide(StoredEvent)}. The automation
 * should make application decisions and record outcomes through commands and events
 * where appropriate.
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
    @Override
    default @Nullable Long getPollingIntervalMs() { return null; }

    /** Override batch size for this automation only. Null = use global default. */
    @Override
    default @Nullable Integer getBatchSize() { return null; }

    /** Override backoff enabled for this automation only. Null = use global default (true). */
    @Override
    default @Nullable Boolean getBackoffEnabled() { return null; }

    /** Override backoff threshold for this automation only. Null = use global default. */
    @Override
    default @Nullable Integer getBackoffThreshold() { return null; }

    /** Override backoff multiplier for this automation only. Null = use global default. */
    @Override
    default @Nullable Integer getBackoffMultiplier() { return null; }

    /** Override max backoff seconds for this automation only. Null = use global default. */
    @Override
    default @Nullable Integer getBackoffMaxSeconds() { return null; }

    /**
     * Called once per matching event to describe what should happen.
     *
     * @param event the matching stored event
     * @return decisions to execute in order; an empty list is treated as a successful no-op
     */
    List<AutomationDecision> decide(StoredEvent event);
}
