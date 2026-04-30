package com.crablet.wallet.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Example HTTP webhook OutboxPublisher for the wallet domain.
 *
 * Delivers each wallet event individually to a configurable webhook URL.
 * Set {@code crablet.wallet.webhook.url} to enable; if blank the publisher
 * skips delivery and logs a warning instead (safe for local dev).
 *
 * Copy and adapt this class for real integrations (Kafka, SNS, Slack, etc.).
 * The key patterns to keep:
 *  - getPreferredMode() INDIVIDUAL — each event gets its own publishBatch call
 *  - throw PublishException on non-retriable errors so the outbox stops retrying
 *  - isHealthy() probes the downstream system; false pauses publishing
 */
@Component
public class WalletWebhookPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(WalletWebhookPublisher.class);

    private final String webhookUrl;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    public WalletWebhookPublisher(
            @Value("${crablet.wallet.webhook.url:}") String webhookUrl,
            ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getName() {
        return "wallet-webhook";
    }

    @Override
    public PublishMode getPreferredMode() {
        return PublishMode.INDIVIDUAL;
    }

    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[WalletWebhookPublisher] crablet.wallet.webhook.url not set — skipping delivery of {} event(s)", events.size());
            return;
        }

        for (StoredEvent event : events) {
            try {
                String payload = buildPayload(event);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-Crablet-Event-Type", event.type())
                        .header("X-Crablet-Event-Position", String.valueOf(event.position()))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 500) {
                    // 5xx — retriable; throw so the outbox retries this event
                    throw new PublishException("Webhook returned " + response.statusCode()
                            + " for event " + event.type() + " at position " + event.position());
                }
                if (response.statusCode() >= 400) {
                    // 4xx — non-retriable; log and continue so the outbox advances past this event
                    log.error("[WalletWebhookPublisher] Non-retriable webhook error {} for event {} at position {}",
                            response.statusCode(), event.type(), event.position());
                }

                log.debug("[WalletWebhookPublisher] Delivered {} position={} → HTTP {}",
                        event.type(), event.position(), response.statusCode());

            } catch (PublishException e) {
                throw e;
            } catch (Exception e) {
                throw new PublishException(
                        "Failed to deliver event " + event.type() + " at position " + event.position(), e);
            }
        }
    }

    @Override
    public boolean isHealthy() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return true; // not configured — considered healthy (no-op mode)
        }
        try {
            HttpRequest probe = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(3))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = http.send(probe, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status < 500;
        } catch (Exception e) {
            log.warn("[WalletWebhookPublisher] Health check failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildPayload(StoredEvent event) throws JsonProcessingException {
        JsonNode data = objectMapper.readTree(new String(event.data(), StandardCharsets.UTF_8));
        List<Map<String, String>> tags = event.tags().stream()
                .map(tag -> Map.of(
                        "key", tag.key() != null ? tag.key() : "",
                        "value", tag.value() != null ? tag.value() : ""))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.type());
        payload.put("position", event.position());
        payload.put("transactionId", event.transactionId());
        payload.put("occurredAt", event.occurredAt());
        payload.put("correlationId", event.correlationId());
        payload.put("causationId", event.causationId());
        payload.put("tags", tags);
        payload.put("data", data);
        return objectMapper.writeValueAsString(payload);
    }
}
