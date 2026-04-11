package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.automations.metrics.AutomationExecutionErrorMetric;
import com.crablet.automations.metrics.AutomationExecutionMetric;
import com.crablet.command.CommandExecutor;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dispatches events to automations — either via HTTP POST (webhook) or directly
 * in-process (via {@link AutomationHandler}).
 *
 * <p>Routing is by automation name: if an {@link AutomationHandler} is registered
 * under the name, the handler's {@link AutomationHandler#react} method is called
 * directly. Otherwise, the corresponding {@link AutomationSubscription}'s webhook URL
 * is used.
 */
public class AutomationDispatcher implements EventHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatcher.class);

    private final Map<String, AutomationSubscription> subscriptions;
    private final Map<String, AutomationHandler> inProcessHandlers;
    private final RestClient restClient;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final Environment environment;

    public AutomationDispatcher(
            Map<String, AutomationSubscription> subscriptions,
            Map<String, AutomationHandler> inProcessHandlers,
            RestClient restClient,
            @Nullable CommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher,
            Environment environment) {
        this.subscriptions = subscriptions;
        this.inProcessHandlers = inProcessHandlers;
        this.restClient = restClient;
        this.commandExecutor = commandExecutor;
        this.eventPublisher = eventPublisher;
        this.environment = environment;
    }

    @Override
    public int handle(String automationName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
        if (inProcessHandlers.containsKey(automationName)) {
            return handleInProcess(automationName, events);
        }

        AutomationSubscription subscription = subscriptions.get(automationName);
        if (subscription == null) {
            log.warn("No subscription or handler registered for automation: {}", automationName);
            return 0;
        }
        return handleWebhook(automationName, subscription, events);
    }

    private int handleInProcess(String automationName, List<StoredEvent> events) throws Exception {
        AutomationHandler handler = inProcessHandlers.get(automationName);
        Instant start = Instant.now();
        int count = 0;

        for (StoredEvent event : events) {
            try {
                handler.react(event, commandExecutor);
                count++;
            } catch (Exception e) {
                eventPublisher.publishEvent(new AutomationExecutionErrorMetric(automationName));
                log.error("Automation {} (in-process) failed on event type={} position={}: {}",
                        automationName, event.type(), event.position(), e.getMessage(), e);
                throw e;
            }
        }

        eventPublisher.publishEvent(new AutomationExecutionMetric(
                automationName, count, Duration.between(start, Instant.now())));
        log.debug("Automation {} (in-process) processed {} events", automationName, count);
        return count;
    }

    private int handleWebhook(String automationName, AutomationSubscription subscription,
                               List<StoredEvent> events) throws Exception {
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
