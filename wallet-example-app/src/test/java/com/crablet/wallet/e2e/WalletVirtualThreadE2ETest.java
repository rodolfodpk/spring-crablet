package com.crablet.wallet.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = WalletVirtualThreadE2ETest.VirtualThreadTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.threads.virtual.enabled=true",
        "spring.main.keep-alive=true",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
            "com.crablet.eventstore.config.EventStoreAutoConfiguration," +
            "com.crablet.eventpoller.config.EventPollerAutoConfiguration," +
            "com.crablet.command.config.CommandAutoConfiguration," +
            "com.crablet.command.web.internal.CommandWebAutoConfiguration," +
            "com.crablet.command.web.internal.CommandWebOpenApiAutoConfiguration," +
            "com.crablet.views.config.ViewsAutoConfiguration," +
            "com.crablet.outbox.config.OutboxAutoConfiguration," +
            "com.crablet.automations.config.AutomationsAutoConfiguration"
    }
)
@DisplayName("Wallet Virtual Thread E2E Tests")
class WalletVirtualThreadE2ETest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should dispatch wallet API requests on virtual threads")
    void shouldDispatchRequestsOnVirtualThreads() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/__test/thread"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"virtual\":true");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class VirtualThreadTestApplication {

        @Bean
        ServletRegistrationBean<HttpServlet> threadDiagnosticsServlet() {
            return new ServletRegistrationBean<>(new ThreadDiagnosticsServlet(), "/__test/thread");
        }
    }

    static class ThreadDiagnosticsServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Thread thread = Thread.currentThread();
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"virtual":%s,"threadName":"%s"}
                    """.formatted(thread.isVirtual(), thread.getName()));
        }
    }
}
