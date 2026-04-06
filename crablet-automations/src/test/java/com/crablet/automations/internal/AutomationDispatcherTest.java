package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AutomationDispatcher}.
 */
@DisplayName("AutomationDispatcher Unit Tests")
class AutomationDispatcherTest {

    private static final CommandExecutor NO_OP_EXECUTOR = mock(CommandExecutor.class);
    private static final ApplicationEventPublisher NO_OP_PUBLISHER = e -> {};

    @Test
    @DisplayName("Should register handlers by automation name")
    void shouldRegisterHandlers_ByAutomationName() throws Exception {
        // Given
        TrackingHandler walletHandler = new TrackingHandler("wallet-notification");
        TrackingHandler orderHandler = new TrackingHandler("order-fulfillment");
        AutomationDispatcher dispatcher = new AutomationDispatcher(
            List.of(walletHandler, orderHandler), NO_OP_EXECUTOR, NO_OP_PUBLISHER);

        List<StoredEvent> events = createTestEvents();

        // When
        dispatcher.handle("wallet-notification", events, null);

        // Then
        assertThat(walletHandler.reactCount).isEqualTo(events.size());
        assertThat(orderHandler.reactCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should delegate to correct handler based on automation name")
    void shouldDelegateToCorrectHandler_BasedOnAutomationName() throws Exception {
        // Given
        TrackingHandler walletHandler = new TrackingHandler("wallet-notification");
        TrackingHandler orderHandler = new TrackingHandler("order-fulfillment");
        AutomationDispatcher dispatcher = new AutomationDispatcher(
            List.of(walletHandler, orderHandler), NO_OP_EXECUTOR, NO_OP_PUBLISHER);

        List<StoredEvent> events = createTestEvents();

        // When
        int count1 = dispatcher.handle("wallet-notification", events, null);
        int count2 = dispatcher.handle("order-fulfillment", events, null);

        // Then
        assertThat(count1).isEqualTo(events.size());
        assertThat(count2).isEqualTo(events.size());
        assertThat(walletHandler.reactCount).isEqualTo(events.size());
        assertThat(orderHandler.reactCount).isEqualTo(events.size());
    }

    @Test
    @DisplayName("Should return 0 when no handler registered for automation name")
    void shouldReturnZero_WhenNoHandlerRegisteredForAutomationName() throws Exception {
        // Given
        AutomationDispatcher dispatcher = new AutomationDispatcher(List.of(), NO_OP_EXECUTOR, NO_OP_PUBLISHER);

        // When
        int result = dispatcher.handle("non-existent-automation", createTestEvents(), null);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("Should pass CommandExecutor to handler")
    void shouldPassCommandExecutor_ToHandler() throws Exception {
        // Given
        CommandExecutorCapturingHandler handler = new CommandExecutorCapturingHandler("automation");
        CommandExecutor executor = mock(CommandExecutor.class);
        AutomationDispatcher dispatcher = new AutomationDispatcher(List.of(handler), executor, NO_OP_PUBLISHER);

        // When
        dispatcher.handle("automation", createTestEvents(), null);

        // Then
        assertThat(handler.receivedExecutor).isSameAs(executor);
    }

    @Test
    @DisplayName("Should process each event individually")
    void shouldProcessEachEvent_Individually() throws Exception {
        // Given
        TrackingHandler handler = new TrackingHandler("automation");
        AutomationDispatcher dispatcher = new AutomationDispatcher(List.of(handler), NO_OP_EXECUTOR, NO_OP_PUBLISHER);
        List<StoredEvent> events = createTestEvents(); // 2 events

        // When
        int count = dispatcher.handle("automation", events, null);

        // Then
        assertThat(count).isEqualTo(2);
        assertThat(handler.reactCount).isEqualTo(2);
        assertThat(handler.receivedEvents).hasSize(2);
    }

    @Test
    @DisplayName("Should propagate exception from handler and stop processing")
    void shouldPropagateException_FromHandlerAndStopProcessing() {
        // Given
        FailingHandler handler = new FailingHandler("automation");
        AutomationDispatcher dispatcher = new AutomationDispatcher(List.of(handler), NO_OP_EXECUTOR, NO_OP_PUBLISHER);
        List<StoredEvent> events = createTestEvents();

        // When & Then
        assertThatThrownBy(() -> dispatcher.handle("automation", events, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Handler failed");
    }

    @Test
    @DisplayName("Should return 0 for empty events list")
    void shouldReturnZero_ForEmptyEventsList() throws Exception {
        // Given
        TrackingHandler handler = new TrackingHandler("automation");
        AutomationDispatcher dispatcher = new AutomationDispatcher(List.of(handler), NO_OP_EXECUTOR, NO_OP_PUBLISHER);

        // When
        int result = dispatcher.handle("automation", List.of(), null);

        // Then
        assertThat(result).isEqualTo(0);
        assertThat(handler.reactCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle last registered handler when same name registered twice")
    void shouldHandleLastRegisteredHandler_WhenSameNameRegisteredTwice() throws Exception {
        // Given
        TrackingHandler first = new TrackingHandler("automation");
        TrackingHandler second = new TrackingHandler("automation");
        AutomationDispatcher dispatcher = new AutomationDispatcher(
            List.of(first, second), NO_OP_EXECUTOR, NO_OP_PUBLISHER);
        List<StoredEvent> events = createTestEvents();

        // When
        dispatcher.handle("automation", events, null);

        // Then - second handler wins (HashMap put overwrites)
        assertThat(second.reactCount).isEqualTo(events.size());
        assertThat(first.reactCount).isEqualTo(0);
    }

    // Test handler implementations

    static class TrackingHandler implements AutomationHandler {
        private final String name;
        int reactCount = 0;
        List<StoredEvent> receivedEvents = new ArrayList<>();

        TrackingHandler(String name) { this.name = name; }

        @Override
        public String getAutomationName() { return name; }

        @Override
        public void react(StoredEvent event, CommandExecutor commandExecutor) {
            reactCount++;
            receivedEvents.add(event);
        }
    }

    static class FailingHandler implements AutomationHandler {
        private final String name;

        FailingHandler(String name) { this.name = name; }

        @Override
        public String getAutomationName() { return name; }

        @Override
        public void react(StoredEvent event, CommandExecutor commandExecutor) {
            throw new RuntimeException("Handler failed");
        }
    }

    static class CommandExecutorCapturingHandler implements AutomationHandler {
        private final String name;
        CommandExecutor receivedExecutor;

        CommandExecutorCapturingHandler(String name) { this.name = name; }

        @Override
        public String getAutomationName() { return name; }

        @Override
        public void react(StoredEvent event, CommandExecutor commandExecutor) {
            this.receivedExecutor = commandExecutor;
        }
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
