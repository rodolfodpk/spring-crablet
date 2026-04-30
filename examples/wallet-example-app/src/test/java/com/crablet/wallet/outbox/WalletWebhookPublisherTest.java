package com.crablet.wallet.outbox;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.crablet.outbox.PublishException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WalletWebhookPublisher Unit Tests")
class WalletWebhookPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should post event payload to configured webhook")
    void shouldPostEventPayload_ToConfiguredWebhook() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        startServer(204, receivedBody);

        WalletWebhookPublisher publisher = new WalletWebhookPublisher(webhookUrl(), objectMapper);

        publisher.publishBatch(List.of(event()));

        String body = receivedBody.get();
        assertThat(body).isNotBlank();

        var json = objectMapper.readTree(body);
        assertThat(json.get("type").asText()).isEqualTo("WalletOpened");
        assertThat(json.get("position").asLong()).isEqualTo(42L);
        assertThat(json.get("transactionId").asText()).isEqualTo("tx-42");
        assertThat(json.get("tags").get(0).get("key").asText()).isEqualTo("wallet_id");
        assertThat(json.get("data").get("walletId").asText()).isEqualTo("wallet-1");
    }

    @Test
    @DisplayName("Should throw PublishException for retriable webhook failure")
    void shouldThrowPublishException_ForRetriableWebhookFailure() throws Exception {
        startServer(503, new AtomicReference<>());

        WalletWebhookPublisher publisher = new WalletWebhookPublisher(webhookUrl(), objectMapper);

        assertThatThrownBy(() -> publisher.publishBatch(List.of(event())))
                .isInstanceOf(PublishException.class)
                .hasMessageContaining("Webhook returned 503");
    }

    private void startServer(int statusCode, AtomicReference<String> receivedBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/events", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(statusCode, -1);
                exchange.close();
                return;
            }

            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
        });
        server.start();
    }

    private String webhookUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/events";
    }

    private StoredEvent event() {
        return new StoredEvent(
                "WalletOpened",
                List.of(Tag.of("wallet_id", "wallet-1")),
                "{\"walletId\":\"wallet-1\",\"owner\":\"Alice\"}".getBytes(StandardCharsets.UTF_8),
                "tx-42",
                42L,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
