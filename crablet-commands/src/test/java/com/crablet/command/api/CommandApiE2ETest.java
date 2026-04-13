package com.crablet.command.api;

import com.crablet.command.api.internal.CommandApiAutoConfiguration;
import com.crablet.command.integration.TestApplication;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.notification.commands.SendWelcomeNotificationCommand;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {TestApplication.class, CommandApiE2ETest.CommandApiTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test,command-api-e2e",
                "crablet.commands.api.enabled=true"
        }
)
@ImportAutoConfiguration(CommandApiAutoConfiguration.class)
@DisplayName("Command API E2E Tests")
class CommandApiE2ETest extends AbstractCrabletTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should execute exposed command and persist events")
    void shouldExecuteExposedCommandAndPersistEvents() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-api-1",
                  "owner": "Alice",
                  "initialBalance": 100
                }
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("status").asText()).isEqualTo("CREATED");
        assertThat(body.path("reason").isNull()).isTrue();

        List<StoredEvent> events = eventRepository.query(Query.forEventAndTag("WalletOpened", "wallet_id", "wallet-api-1"), null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should return idempotent response for duplicate idempotent command")
    void shouldReturnIdempotentResponseForDuplicateIdempotentCommand() throws Exception {
        HttpResponse<String> first = postJson("""
                {
                  "commandType": "send_welcome_notification",
                  "walletId": "wallet-api-2",
                  "owner": "Bob"
                }
                """);
        HttpResponse<String> second = postJson("""
                {
                  "commandType": "send_welcome_notification",
                  "walletId": "wallet-api-2",
                  "owner": "Bob"
                }
                """);

        assertThat(first.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(second.statusCode()).isEqualTo(HttpStatus.OK.value());

        JsonNode body = objectMapper.readTree(second.body());
        assertThat(body.get("status").asText()).isEqualTo("IDEMPOTENT");
        assertThat(body.get("reason").asText()).isEqualTo("DUPLICATE_OPERATION");

        List<StoredEvent> events = eventRepository.query(Query.forEventAndTag("WelcomeNotificationSent", "wallet_id", "wallet-api-2"), null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should return conflict for duplicate open wallet command")
    void shouldReturnConflictForDuplicateOpenWalletCommand() throws Exception {
        HttpResponse<String> first = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-api-3",
                  "owner": "Charlie",
                  "initialBalance": 50
                }
                """);
        HttpResponse<String> second = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-api-3",
                  "owner": "Charlie",
                  "initialBalance": 50
                }
                """);

        assertThat(first.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(second.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("Should reject non exposed command")
    void shouldRejectNonExposedCommand() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "deposit",
                  "depositId": "dep-hidden",
                  "walletId": "wallet-hidden",
                  "amount": 10,
                  "description": "hidden"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Should reject unknown command type")
    void shouldRejectUnknownCommandType() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "does_not_exist",
                  "walletId": "wallet-x"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should reject malformed JSON")
    void shouldRejectMalformedJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"commandType\":"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    private HttpResponse<String> postJson(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    @Profile("command-api-e2e")
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
