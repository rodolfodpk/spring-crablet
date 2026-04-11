package com.crablet.automations.internal;

import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dispatches events to automation webhook endpoints via HTTP POST.
 * <p>
 * For each matching event, fires a POST request to the URL configured in the
 * corresponding {@link AutomationSubscription}. The payload is the event serialized
 * as JSON. On HTTP 4xx/5xx or network errors, throws so the event processor retries.
 */
public class AutomationDispatcher implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatcher.class);

    private final Map<String, AutomationSubscription> subscriptions;
    private final RestClient restClient;
    private final ApplicationEventPublisher eventPublisher;
    private final Environment environment;

    public AutomationDispatcher(
            Map<String, AutomationSubscription> subscriptions,
            RestClient restClient,
            ApplicationEventPublisher eventPublisher,
            Environment environment) {
        this.subscriptions = subscriptions;
        this.restClient = restClient;
        this.eventPublisher = eventPublisher;
        this.environment = environment;
    }

    @Override
    public int handle(String automationName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
        AutomationSubscription subscription = subscriptions.get(automationName);
        if (subscription == null) {
            log.warn("No subscription registered for automation: {}", automationName);
            return 0;
        }

        String webhookUrl = resolveWebhookUrl(subscription.getWebhookUrl());
        Instant start = Instant.now();
        int count = 0;

        for (StoredEvent event : events) {
            try {
                postEvent(automationName, webhookUrl, subscription.getWebhookHeaders(), event);
                count++;
            } catch (Exception e) {
                eventPublisher.publishEvent(new AutomationExecutionErrorMetric(automationName));
                log.error("Automation {} failed to POST event type={} position={} to {}: {}",
                    automationName, event.type(), event.position(), webhookUrl, e.getMessage(), e);
                throw e;
            }
        }

        eventPublisher.publishEvent(new AutomationExecutionMetric(
                automationName, count, Duration.between(start, Instant.now())));
        log.debug("Automation {} dispatched {} events to {}", automationName, count, webhookUrl);
        return count;
    }

    private void postEvent(String automationName, String webhookUrl,
                           Map<String, String> headers, StoredEvent event) {
        String payload = buildPayload(event);

        var requestSpec = restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);

        for (var entry : headers.entrySet()) {
            requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
        }

        requestSpec.retrieve()
                .toBodilessEntity();

        log.debug("Automation {} delivered event type={} position={}", automationName, event.type(), event.position());
    }

    private String resolveWebhookUrl(String webhookUrl) {
        if (webhookUrl.startsWith("http://") || webhookUrl.startsWith("https://")) {
            return webhookUrl;
        }
        if (!webhookUrl.startsWith("/")) {
            throw new IllegalArgumentException("webhookUrl must be absolute or start with '/': " + webhookUrl);
        }

        Integer port = environment.getProperty("local.server.port", Integer.class);
        if (port == null || port <= 0) {
            port = environment.getProperty("server.port", Integer.class);
        }
        if (port == null || port <= 0) {
            throw new IllegalStateException(
                    "Relative automation webhookUrl requires local.server.port or server.port: " + webhookUrl
            );
        }

        return "http://localhost:" + port + webhookUrl;
    }

    /**
     * Serializes a StoredEvent as a JSON object.
     * The {@code data} field is inlined as raw JSON (not base64) since it is already a JSON byte array.
     */
    private String buildPayload(StoredEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"type\":").append(jsonString(event.type())).append(',');
        sb.append("\"position\":").append(event.position()).append(',');
        sb.append("\"transactionId\":").append(jsonString(event.transactionId())).append(',');
        sb.append("\"occurredAt\":").append(jsonString(event.occurredAt().toString())).append(',');
        sb.append("\"tags\":[");
        List<Tag> tags = event.tags();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            Tag tag = tags.get(i);
            sb.append("{\"key\":").append(jsonString(tag.key()))
              .append(",\"value\":").append(jsonString(tag.value())).append('}');
        }
        sb.append("],");
        // Inline the event data as raw JSON
        sb.append("\"data\":").append(new String(event.data()));
        sb.append('}');
        return sb.toString();
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
