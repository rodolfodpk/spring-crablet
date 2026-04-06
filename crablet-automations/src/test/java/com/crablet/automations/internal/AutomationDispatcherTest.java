package com.crablet.automations.internal;

import com.crablet.automations.AutomationSubscription;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AutomationDispatcher}.
 */
@DisplayName("AutomationDispatcher Unit Tests")
class AutomationDispatcherTest {

    private static final ApplicationEventPublisher NO_OP_PUBLISHER = e -> {};

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec postSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        postSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(null);
    }

    @Test
    @DisplayName("Should POST to webhook URL for matching automation")
    void shouldPostToWebhookUrl_ForMatchingAutomation() throws Exception {
        // Given
        AutomationSubscription subscription = AutomationSubscription.builder("wallet-notification")
                .webhookUrl("http://localhost:8080/api/automations/wallet-opened")
                .build();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("wallet-notification", subscription), restClient, NO_OP_PUBLISHER);

        // When
        int count = dispatcher.handle("wallet-notification", createTestEvents(), null);

        // Then — one POST per event (2 events total)
        assertThat(count).isEqualTo(2);
        verify(postSpec, times(2)).uri("http://localhost:8080/api/automations/wallet-opened");
    }

    @Test
    @DisplayName("Should return 0 when no subscription registered for automation name")
    void shouldReturnZero_WhenNoSubscriptionRegistered() throws Exception {
        // Given
        AutomationDispatcher dispatcher = new AutomationDispatcher(Map.of(), restClient, NO_OP_PUBLISHER);

        // When
        int result = dispatcher.handle("non-existent-automation", createTestEvents(), null);

        // Then
        assertThat(result).isEqualTo(0);
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should process each event individually via separate POST requests")
    void shouldPostForEachEvent_Individually() throws Exception {
        // Given
        AutomationSubscription subscription = AutomationSubscription.builder("automation")
                .webhookUrl("http://localhost:8080/api/automations/handler")
                .build();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", subscription), restClient, NO_OP_PUBLISHER);
        List<StoredEvent> events = createTestEvents(); // 2 events

        // When
        int count = dispatcher.handle("automation", events, null);

        // Then - one POST per event (2 events)
        assertThat(count).isEqualTo(2);
        verify(restClient, times(2)).post();
    }

    @Test
    @DisplayName("Should include webhook headers in POST request")
    void shouldIncludeWebhookHeaders_InPostRequest() throws Exception {
        // Given
        AutomationSubscription subscription = AutomationSubscription.builder("automation")
                .webhookUrl("http://localhost:8080/api/automations/handler")
                .webhookHeaders(Map.of("Authorization", "Bearer secret-token"))
                .build();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", subscription), restClient, NO_OP_PUBLISHER);

        // When
        dispatcher.handle("automation", List.of(createTestEvents().get(0)), null);

        // Then
        verify(bodySpec).header("Authorization", "Bearer secret-token");
    }

    @Test
    @DisplayName("Should return 0 for empty events list")
    void shouldReturnZero_ForEmptyEventsList() throws Exception {
        // Given
        AutomationSubscription subscription = AutomationSubscription.builder("automation")
                .webhookUrl("http://localhost:8080/api/automations/handler")
                .build();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", subscription), restClient, NO_OP_PUBLISHER);

        // When
        int result = dispatcher.handle("automation", List.of(), null);

        // Then
        assertThat(result).isEqualTo(0);
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should propagate exception from RestClient and stop processing")
    void shouldPropagateException_FromRestClientAndStopProcessing() {
        // Given
        when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("Connection refused"));

        AutomationSubscription subscription = AutomationSubscription.builder("automation")
                .webhookUrl("http://localhost:8080/api/automations/handler")
                .build();
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", subscription), restClient, NO_OP_PUBLISHER);

        // When & Then
        assertThatThrownBy(() -> dispatcher.handle("automation", createTestEvents(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Connection refused");
    }

    private List<StoredEvent> createTestEvents() {
        return List.of(
            new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-1")),
                "{\"walletId\":\"wallet-1\"}".getBytes(),
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
