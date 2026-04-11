package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.automations.AutomationSubscription;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

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

/**
 * Unit tests for {@link AutomationDispatcher}.
 * Webhook tests use WireMock for real HTTP verification.
 * In-process tests use a direct handler invocation.
 */
@WireMockTest
@DisplayName("AutomationDispatcher Unit Tests")
class AutomationDispatcherTest {

    private static final ApplicationEventPublisher NO_OP_PUBLISHER = e -> {};

    private AutomationDispatcher webhookDispatcher(String automationName, String webhookUrl,
                                                    WireMockRuntimeInfo wm) {
        AutomationSubscription subscription = AutomationSubscription.builder(automationName)
                .webhookUrl(webhookUrl)
                .build();
        return new AutomationDispatcher(
                Map.of(automationName, subscription), Map.of(),
                RestClient.builder().build(), null,
                NO_OP_PUBLISHER, envWithNoPort());
    }

    private AutomationDispatcher webhookDispatcherWithHeaders(String automationName, String webhookUrl,
                                                               Map<String, String> headers,
                                                               WireMockRuntimeInfo wm) {
        AutomationSubscription subscription = AutomationSubscription.builder(automationName)
                .webhookUrl(webhookUrl)
                .webhookHeaders(headers)
                .build();
        return new AutomationDispatcher(
                Map.of(automationName, subscription), Map.of(),
                RestClient.builder().build(), null,
                NO_OP_PUBLISHER, envWithNoPort());
    }

    @Test
    @DisplayName("Should POST to webhook URL for each matching event")
    void shouldPostToWebhookUrl_ForEachMatchingEvent(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/webhook").willReturn(ok()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcher("automation", webhookUrl, wm);

        int count = dispatcher.handle("automation", createTestEvents(), null);

        assertThat(count).isEqualTo(2);
        verify(2, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @DisplayName("Should return 0 when no subscription or handler registered for automation name")
    void shouldReturnZero_WhenNothingRegistered(WireMockRuntimeInfo wm) throws Exception {
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), Map.of(), RestClient.builder().build(), null,
                NO_OP_PUBLISHER, envWithNoPort());

        int result = dispatcher.handle("non-existent-automation", createTestEvents(), null);

        assertThat(result).isEqualTo(0);
        verify(0, newRequestPattern());
    }

    @Test
    @DisplayName("Should return 0 for empty events list")
    void shouldReturnZero_ForEmptyEventsList(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/webhook").willReturn(ok()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcher("automation", webhookUrl, wm);

        int result = dispatcher.handle("automation", List.of(), null);

        assertThat(result).isEqualTo(0);
        verify(0, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @DisplayName("Should include webhook headers in POST request")
    void shouldIncludeWebhookHeaders_InPostRequest(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/webhook").willReturn(ok()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcherWithHeaders(
                "automation", webhookUrl, Map.of("Authorization", "Bearer secret-token"), wm);

        dispatcher.handle("automation", List.of(createTestEvents().get(0)), null);

        verify(1, postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("Authorization", equalTo("Bearer secret-token")));
    }

    @Test
    @DisplayName("Should propagate HTTP error from webhook and stop processing")
    void shouldPropagateHttpError_FromWebhookAndStopProcessing(WireMockRuntimeInfo wm) {
        stubFor(post("/webhook").willReturn(serverError()));

        String webhookUrl = wm.getHttpBaseUrl() + "/webhook";
        AutomationDispatcher dispatcher = webhookDispatcher("automation", webhookUrl, wm);

        assertThatThrownBy(() -> dispatcher.handle("automation", createTestEvents(), null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should resolve relative webhook URL against active web server port")
    void shouldResolveRelativeWebhookUrl_AgainstActiveWebServerPort(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/api/automations/handler").willReturn(ok()));

        AutomationSubscription subscription = AutomationSubscription.builder("automation")
                .webhookUrl("/api/automations/handler")
                .build();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", subscription), Map.of(),
                RestClient.builder().build(), null,
                NO_OP_PUBLISHER, envWithPort(wm.getHttpPort()));

        int count = dispatcher.handle("automation", List.of(createTestEvents().get(0)), null);

        assertThat(count).isEqualTo(1);
        verify(1, postRequestedFor(urlEqualTo("/api/automations/handler")));
    }

    @Test
    @DisplayName("Should call in-process handler directly without HTTP")
    void shouldCallInProcessHandler_DirectlyWithoutHttp(WireMockRuntimeInfo wm) throws Exception {
        CommandExecutor executor = mock(CommandExecutor.class);
        AtomicReference<StoredEvent> received = new AtomicReference<>();

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) { received.set(event); }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), Map.of("automation", handler),
                RestClient.builder().build(), executor,
                NO_OP_PUBLISHER, envWithNoPort());

        int count = dispatcher.handle("automation", List.of(createTestEvents().get(0)), null);

        assertThat(count).isEqualTo(1);
        assertThat(received.get()).isNotNull();
        assertThat(received.get().type()).isEqualTo("WalletOpened");
        verify(0, newRequestPattern()); // no HTTP calls
    }

    @Test
    @DisplayName("Should pass CommandExecutor to in-process handler")
    void shouldPassCommandExecutor_ToInProcessHandler(WireMockRuntimeInfo wm) throws Exception {
        CommandExecutor executor = mock(CommandExecutor.class);
        AtomicReference<CommandExecutor> receivedExecutor = new AtomicReference<>();

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) { receivedExecutor.set(ce); }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), Map.of("automation", handler),
                RestClient.builder().build(), executor,
                NO_OP_PUBLISHER, envWithNoPort());

        dispatcher.handle("automation", List.of(createTestEvents().get(0)), null);

        assertThat(receivedExecutor.get()).isSameAs(executor);
    }

    @Test
    @DisplayName("Should propagate exception from in-process handler")
    void shouldPropagateException_FromInProcessHandler(WireMockRuntimeInfo wm) {
        CommandExecutor executor = mock(CommandExecutor.class);

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) {
                throw new RuntimeException("handler error");
            }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), Map.of("automation", handler),
                RestClient.builder().build(), executor,
                NO_OP_PUBLISHER, envWithNoPort());

        assertThatThrownBy(() -> dispatcher.handle("automation", List.of(createTestEvents().get(0)), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("handler error");
    }

    // --- helpers ---

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
