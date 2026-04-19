package com.crablet.command.web;

import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {TestApplication.class, CommandApiPackageExposureE2ETest.PackageExposureConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=test"
)
@DisplayName("Command API Package Exposure E2E Tests")
class CommandApiPackageExposureE2ETest extends AbstractCrabletTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should expose commands from configured package")
    void shouldExposeCommandsFromConfiguredPackage() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-pkg-1",
                  "owner": "Alice",
                  "initialBalance": 100
                }
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    @DisplayName("Should block commands outside configured package")
    void shouldBlockCommandsOutsideConfiguredPackage() throws Exception {
        HttpResponse<String> response = postJson("""
                {
                  "commandType": "send_welcome_notification",
                  "walletId": "wallet-pkg-2",
                  "owner": "Bob"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("GET /api/commands should list only wallet commands")
    void shouldListOnlyExposedPackageCommands() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());

        JsonNode body = objectMapper.readTree(response.body());
        List<String> types = new ArrayList<>();
        body.get("exposedCommands").forEach(node -> types.add(node.get("commandType").asText()));

        assertThat(types).doesNotContain("send_welcome_notification");
        assertThat(types).contains("open_wallet");
    }

    private HttpResponse<String> postJson(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class PackageExposureConfig {
        @Bean
        CommandApiExposedCommands commandApiExposedCommands() {
            return CommandApiExposedCommands.fromPackages("com.crablet.examples.wallet");
        }
    }
}
