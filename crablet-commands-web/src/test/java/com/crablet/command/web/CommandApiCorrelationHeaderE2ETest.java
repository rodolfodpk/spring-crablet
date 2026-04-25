package com.crablet.command.web;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.examples.wallet.notification.commands.SendWelcomeNotificationCommand;
import com.crablet.test.AbstractCrabletTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {TestApplication.class, CommandApiCorrelationHeaderE2ETest.CommandApiTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "crablet.commands.api.correlation-header-enabled=true"
        }
)
@DisplayName("Command API Correlation Header E2E Tests")
class CommandApiCorrelationHeaderE2ETest extends AbstractCrabletTest {

    @Autowired
    private EventRepository eventRepository;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should echo supplied correlation ID and store it on appended events")
    void shouldEchoSuppliedCorrelationIdAndStoreItOnEvents() throws Exception {
        UUID correlationId = UUID.randomUUID();
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-correlation-1",
                  "owner": "Alice",
                  "initialBalance": 100
                }
                """, correlationId.toString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.headers().firstValue("X-Correlation-Id")).contains(correlationId.toString());

        List<StoredEvent> events = eventRepository.query(
                Query.forEventAndTag("WalletOpened", "wallet_id", "wallet-correlation-1"), null);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().correlationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Should generate and echo a correlation ID when header is missing")
    void shouldGenerateAndEchoCorrelationIdWhenMissing() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-correlation-2",
                  "owner": "Bob",
                  "initialBalance": 50
                }
                """, null);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        String correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    @DisplayName("Should reject malformed correlation ID")
    void shouldRejectMalformedCorrelationId() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-correlation-3",
                  "owner": "Charlie",
                  "initialBalance": 50
                }
                """, "not-a-uuid");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.body()).contains("Invalid X-Correlation-Id header");
    }

    private HttpResponse<String> postJson(String json, @Nullable String correlationId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (correlationId != null) {
            builder.header("X-Correlation-Id", correlationId);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class CommandApiTestConfig {
        @Bean
        CommandApiExposedCommands commandApiExposedCommands() {
            return CommandApiExposedCommands.of(
                    OpenWalletCommand.class,
                    SendWelcomeNotificationCommand.class
            );
        }
    }
}
