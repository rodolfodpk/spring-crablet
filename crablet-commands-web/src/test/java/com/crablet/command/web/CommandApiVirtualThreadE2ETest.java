package com.crablet.command.web;

import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
        classes = {TestApplication.class, CommandApiVirtualThreadE2ETest.VirtualThreadTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.threads.virtual.enabled=true",
                "spring.main.keep-alive=true"
        }
)
@DisplayName("Command API Virtual Thread E2E Tests")
class CommandApiVirtualThreadE2ETest extends AbstractCrabletTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should dispatch Tomcat command API requests on virtual threads")
    void shouldDispatchTomcatRequestsOnVirtualThreads() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/__test/thread"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"virtual\":true");
    }

    @TestConfiguration
    static class VirtualThreadTestConfig {

        @Bean
        ServletRegistrationBean<HttpServlet> threadDiagnosticsServlet() {
            return new ServletRegistrationBean<>(new ThreadDiagnosticsServlet(), "/__test/thread");
        }

        @Bean
        CommandApiExposedCommands commandApiExposedCommands() {
            return CommandApiExposedCommands.of(OpenWalletCommand.class);
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
