package com.crablet.command.api;

import com.crablet.command.api.internal.CommandApiAutoConfiguration;
import com.crablet.command.integration.TestApplication;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {TestApplication.class, CommandApiCustomBasePathE2ETest.CommandApiBasePathConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test,command-api-custom",
                "crablet.commands.api.enabled=true",
                "crablet.commands.api.base-path=/api/custom-commands"
        }
)
@ImportAutoConfiguration(CommandApiAutoConfiguration.class)
@DisplayName("Command API Custom Base Path E2E Tests")
class CommandApiCustomBasePathE2ETest extends AbstractCrabletTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should honor custom base path")
    void shouldHonorCustomBasePath() throws Exception {
        String payload = """
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-api-custom",
                  "owner": "Diana",
                  "initialBalance": 10
                }
                """;

        HttpRequest customPathRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/custom-commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpRequest defaultPathRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> customPathResponse = client.send(customPathRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> defaultPathResponse = client.send(defaultPathRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(customPathResponse.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(defaultPathResponse.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @TestConfiguration
    @Profile("command-api-custom")
    static class CommandApiBasePathConfig {
        @Bean
        CommandApiExposedCommands commandApiExposedCommands() {
            return CommandApiExposedCommands.of(OpenWalletCommand.class);
        }
    }
}
