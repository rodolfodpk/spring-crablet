package com.crablet.automations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Declares which events trigger an automation and where to deliver them.
 * <p>
 * When a matching event is received, the automations module fires an HTTP POST
 * to {@link #getWebhookUrl()} with the event serialized as JSON.
 * <p>
 * Register an {@code AutomationSubscription} bean for each automation:
 * <pre>{@code
 * @Bean
 * AutomationSubscription walletOpened() {
 *     return AutomationSubscription.builder("wallet-opened-welcome-notification")
 *         .eventTypes(type(WalletOpened.class))
 *         .webhookUrl("http://localhost:8080/api/automations/wallet-opened")
 *         .build();
 * }
 * }</pre>
 */
public class AutomationSubscription {

    private final String automationName;
    private final Set<String> eventTypes;
    private final Set<String> requiredTags;
    private final Set<String> anyOfTags;
    private final String webhookUrl;
    private final Map<String, String> webhookHeaders;
    private final int webhookTimeoutMs;

    protected AutomationSubscription(
            String automationName,
            Set<String> eventTypes,
            Set<String> requiredTags,
            Set<String> anyOfTags,
            String webhookUrl,
            Map<String, String> webhookHeaders,
            int webhookTimeoutMs) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl must not be null or blank for automation: " + automationName);
        }
        this.automationName = automationName;
        this.eventTypes = eventTypes != null ? Set.copyOf(eventTypes) : Set.of();
        this.requiredTags = requiredTags != null ? Set.copyOf(requiredTags) : Set.of();
        this.anyOfTags = anyOfTags != null ? Set.copyOf(anyOfTags) : Set.of();
        this.webhookUrl = webhookUrl;
        this.webhookHeaders = webhookHeaders != null ? Collections.unmodifiableMap(new HashMap<>(webhookHeaders)) : Map.of();
        this.webhookTimeoutMs = webhookTimeoutMs;
    }

    public String getAutomationName() { return automationName; }
    public Set<String> getEventTypes() { return eventTypes; }
    public Set<String> getRequiredTags() { return requiredTags; }
    public Set<String> getAnyOfTags() { return anyOfTags; }
    public String getWebhookUrl() { return webhookUrl; }
    public Map<String, String> getWebhookHeaders() { return webhookHeaders; }
    public int getWebhookTimeoutMs() { return webhookTimeoutMs; }

    /** Entry point for building a subscription. */
    public static Builder builder(String automationName) {
        return new Builder(automationName);
    }

    public static class Builder {
        private final String automationName;
        private Set<String> eventTypes = Set.of();
        private Set<String> requiredTags = Set.of();
        private Set<String> anyOfTags = Set.of();
        private String webhookUrl;
        private Map<String, String> webhookHeaders = Map.of();
        private int webhookTimeoutMs = 5000;

        public Builder(String automationName) {
            this.automationName = automationName;
        }

        /** Filter by event types (Set overload). */
        public Builder eventTypes(Set<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        /** Filter by event types (varargs overload). Use {@code type(MyEvent.class)} for type safety. */
        public Builder eventTypes(String... eventTypes) {
            this.eventTypes = new HashSet<>(Arrays.asList(eventTypes));
            return this;
        }

        /** ALL of these tag keys must be present on the event for it to be delivered. */
        public Builder requiredTags(String... tagKeys) {
            this.requiredTags = new HashSet<>(Arrays.asList(tagKeys));
            return this;
        }

        /** At least ONE of these tag keys must be present on the event for it to be delivered. */
        public Builder anyOfTags(String... tagKeys) {
            this.anyOfTags = new HashSet<>(Arrays.asList(tagKeys));
            return this;
        }

        /** HTTP URL to POST matching events to. Required. */
        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        /** Static HTTP headers to include in every webhook request (e.g. authorization). */
        public Builder webhookHeaders(Map<String, String> headers) {
            this.webhookHeaders = headers;
            return this;
        }

        /** Per-request timeout in milliseconds. Defaults to 5000ms. */
        public Builder webhookTimeoutMs(int timeoutMs) {
            this.webhookTimeoutMs = timeoutMs;
            return this;
        }

        /** Builds the {@link AutomationSubscription}. */
        public AutomationSubscription build() {
            return new AutomationSubscription(automationName, eventTypes, requiredTags, anyOfTags,
                    webhookUrl, webhookHeaders, webhookTimeoutMs);
        }
    }
}
