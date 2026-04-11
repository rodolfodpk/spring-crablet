package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dedicated webhook transport for automations using Spring's RestClient stack.
 */
public class AutomationWebhookClient {

    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
    private final ObjectMapper objectMapper;
    private final List<Consumer<RestClient.Builder>> builderCustomizers;

    public AutomationWebhookClient(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                   ObjectMapper objectMapper,
                                   List<Consumer<RestClient.Builder>> builderCustomizers) {
        this.restClientBuilderProvider = restClientBuilderProvider;
        this.objectMapper = objectMapper;
        this.builderCustomizers = List.copyOf(builderCustomizers);
    }

    public void postEvent(String resolvedWebhookUrl, AutomationHandler handler, StoredEvent event) {
        RestClient.Builder builder = restClientBuilderProvider.getObject().clone()
                .requestFactory(requestFactory(handler.getWebhookTimeoutMs()));

        for (Consumer<RestClient.Builder> customizer : builderCustomizers) {
            customizer.accept(builder);
        }

        RestClient restClient = builder.build();

        var requestSpec = restClient.post()
                .uri(resolvedWebhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toPayload(event));

        for (Map.Entry<String, String> entry : handler.getWebhookHeaders().entrySet()) {
            requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
        }

        requestSpec.retrieve().toBodilessEntity();
    }

    private ClientHttpRequestFactory requestFactory(int timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(timeout, timeout);
        return ClientHttpRequestFactoryBuilder.simple().build(settings);
    }

    private AutomationWebhookPayload toPayload(StoredEvent event) {
        try {
            JsonNode eventData = objectMapper.readTree(event.data());
            List<AutomationWebhookTag> tags = event.tags().stream()
                    .map(this::toWebhookTag)
                    .toList();
            return new AutomationWebhookPayload(
                    event.type(),
                    event.position(),
                    event.transactionId(),
                    event.occurredAt(),
                    tags,
                    eventData
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to serialize automation event payload for type=" + event.type() +
                            " position=" + event.position(),
                    e
            );
        }
    }

    private AutomationWebhookTag toWebhookTag(Tag tag) {
        return new AutomationWebhookTag(tag.key(), tag.value());
    }

    private record AutomationWebhookPayload(
            String type,
            long position,
            String transactionId,
            Instant occurredAt,
            List<AutomationWebhookTag> tags,
            JsonNode data
    ) {}

    private record AutomationWebhookTag(String key, String value) {}
}
