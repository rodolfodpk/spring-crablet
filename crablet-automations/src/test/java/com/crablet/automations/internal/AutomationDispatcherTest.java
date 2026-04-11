package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest
@DisplayName("AutomationDispatcher Unit Tests")
class AutomationDispatcherTest {

    private static final ApplicationEventPublisher NO_OP_PUBLISHER = e -> {};

    private AutomationDispatcher webhookDispatcher(String automationName, String webhookUrl) {
        AutomationHandler handler = webhookHandler(automationName, webhookUrl, Map.of());
        return new AutomationDispatcher(
                Map.of(automationName, handler),
                webhookClient(), null,
                NO_OP_PUBLISHER, envWithNoPort());
    }

    private AutomationDispatcher webhookDispatcherWithHeaders(String automationName, String webhookUrl,
                                                             Map<String, String> headers) {
        AutomationHandler handler = webhookHandler(automationName, webhookUrl, headers);
        return new AutomationDispatcher(
                Map.of(automationName, handler),
                webhookClient(), null,
                NO_OP_PUBLISHER, envWithNoPort());
    }

    @Test
    @DisplayName("Should POST to webhook URL for each matching event")
    void shouldPostToWebhookUrlForEachMatchingEvent(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/webhook").willReturn(ok()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcher("automation", webhookUrl);

        int count = dispatcher.handle("automation", createTestEvents());

        assertThat(count).isEqualTo(2);
        verify(2, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @DisplayName("Should return 0 when no handler is registered for automation name")
    void shouldReturnZeroWhenNothingRegistered() throws Exception {
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), webhookClient(), null,
                NO_OP_PUBLISHER, envWithNoPort());

        int result = dispatcher.handle("non-existent-automation", createTestEvents());

        assertThat(result).isEqualTo(0);
        verify(0, newRequestPattern());
    }

    @Test
    @DisplayName("Should return 0 for empty events list")
    void shouldReturnZeroForEmptyEventsList(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/webhook").willReturn(ok()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcher("automation", webhookUrl);

        int result = dispatcher.handle("automation", List.of());

        assertThat(result).isEqualTo(0);
        verify(0, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @DisplayName("Should include webhook headers in POST request")
    void shouldIncludeWebhookHeadersInPostRequest(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/webhook").willReturn(ok()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcherWithHeaders(
                "automation", webhookUrl, Map.of("Authorization", "Bearer secret-token"));

        dispatcher.handle("automation", List.of(createTestEvents().get(0)));

        verify(1, postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("Authorization", equalTo("Bearer secret-token")));
    }

    @Test
    @DisplayName("Should propagate HTTP error from webhook and stop processing")
    void shouldPropagateHttpErrorFromWebhookAndStopProcessing(WireMockRuntimeInfo wm) {
        stubFor(post("/webhook").willReturn(serverError()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcher("automation", webhookUrl);

        assertThatThrownBy(() -> dispatcher.handle("automation", createTestEvents()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should resolve relative webhook URL against active web server port")
    void shouldResolveRelativeWebhookUrlAgainstActiveWebServerPort(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/api/automations/handler").willReturn(ok()));

        AutomationHandler handler = webhookHandler("automation", "/api/automations/handler", Map.of());
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", handler),
                webhookClient(), null,
                NO_OP_PUBLISHER, envWithPort(wm.getHttpPort()));

        int count = dispatcher.handle("automation", List.of(createTestEvents().get(0)));

        assertThat(count).isEqualTo(1);
        verify(1, postRequestedFor(urlEqualTo("/api/automations/handler")));
    }

    @Test
    @DisplayName("Should call in-process handler directly without HTTP")
    void shouldCallInProcessHandlerDirectlyWithoutHttp() throws Exception {
        CommandExecutor executor = mock(CommandExecutor.class);
        AtomicReference<StoredEvent> received = new AtomicReference<>();

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) { received.set(event); }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", handler),
                webhookClient(), executor,
                NO_OP_PUBLISHER, envWithNoPort());

        int count = dispatcher.handle("automation", List.of(createTestEvents().get(0)));

        assertThat(count).isEqualTo(1);
        assertThat(received.get()).isNotNull();
        assertThat(received.get().type()).isEqualTo("WalletOpened");
        verify(0, newRequestPattern());
    }

    @Test
    @DisplayName("Should pass CommandExecutor to in-process handler")
    void shouldPassCommandExecutorToInProcessHandler() throws Exception {
        CommandExecutor executor = mock(CommandExecutor.class);
        AtomicReference<CommandExecutor> receivedExecutor = new AtomicReference<>();

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) { receivedExecutor.set(ce); }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", handler),
                webhookClient(), executor,
                NO_OP_PUBLISHER, envWithNoPort());

        dispatcher.handle("automation", List.of(createTestEvents().get(0)));

        assertThat(receivedExecutor.get()).isSameAs(executor);
    }

    @Test
    @DisplayName("Should propagate exception from in-process handler")
    void shouldPropagateExceptionFromInProcessHandler() {
        CommandExecutor executor = mock(CommandExecutor.class);

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) {
                throw new RuntimeException("handler error");
            }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", handler),
                webhookClient(), executor,
                NO_OP_PUBLISHER, envWithNoPort());

        assertThatThrownBy(() -> dispatcher.handle("automation", List.of(createTestEvents().get(0))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("handler error");
    }

    private AutomationHandler webhookHandler(String automationName, String webhookUrl, Map<String, String> headers) {
        return new AutomationHandler() {
            @Override public String getAutomationName() { return automationName; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public String getWebhookUrl() { return webhookUrl; }
            @Override public Map<String, String> getWebhookHeaders() { return headers; }
        };
    }

    private AutomationWebhookClient webhookClient() {
        ObjectProvider<RestClient.Builder> builderProvider = providerOf(RestClient.builder());
        ObjectMapper objectMapper = JsonMapper.builder().build();
        return new AutomationWebhookClient(builderProvider, objectMapper, List.of());
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
        };
    }

    private Environment envWithPort(int port) {
        Environment env = mock(Environment.class);
        when(env.getProperty("local.server.port", Integer.class)).thenReturn(port);
        when(env.getProperty("server.port", Integer.class)).thenReturn(null);
        return env;
    }

    private Environment envWithNoPort() {
        Environment env = mock(Environment.class);
        when(env.getProperty("local.server.port", Integer.class)).thenReturn(null);
        when(env.getProperty("server.port", Integer.class)).thenReturn(null);
        return env;
    }

    private List<StoredEvent> createTestEvents() {
        return List.of(
            new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-1")),
                "{\"wallet_id\":\"wallet-1\",\"owner\":\"Alice\"}".getBytes(),
                "tx-1",
                1L,
                Instant.now()
            ),
            new StoredEvent(
                "DepositMade",
                List.of(new Tag("wallet_id", "wallet-1")),
                "{\"amount\":100}".getBytes(),
                "tx-2",
                2L,
                Instant.now()
            )
        );
    }
}
