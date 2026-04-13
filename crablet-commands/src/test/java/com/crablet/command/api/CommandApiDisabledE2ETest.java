package com.crablet.command.api;

import com.crablet.command.api.internal.CommandApiAutoConfiguration;
import com.crablet.command.integration.TestApplication;
import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=test"
)
@ImportAutoConfiguration(CommandApiAutoConfiguration.class)
@DisplayName("Command API Disabled E2E Tests")
class CommandApiDisabledE2ETest extends AbstractCrabletTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should not expose command API when disabled by default")
    void shouldNotExposeCommandApiWhenDisabledByDefault() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                {
                  "commandType": "open_wallet",
                  "walletId": "wallet-disabled",
                  "owner": "Alice",
                  "initialBalance": 100
                }
                """))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
