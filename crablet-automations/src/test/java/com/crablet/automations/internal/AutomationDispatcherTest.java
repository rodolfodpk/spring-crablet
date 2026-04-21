package com.crablet.automations.internal;

import com.crablet.automations.AutomationHandler;
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AutomationDispatcher Unit Tests")
class AutomationDispatcherTest {

    private static final ApplicationEventPublisher NO_OP_PUBLISHER = e -> {};

    @Test
    @DisplayName("Should return 0 when no handler is registered for automation name")
    void shouldReturnZeroWhenNothingRegistered() throws Exception {
        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of(), mock(CommandExecutor.class), NO_OP_PUBLISHER);

        int result = dispatcher.handle("non-existent-automation", createTestEvents());

        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("Should call handler directly for each event")
    void shouldCallHandlerDirectlyForEachEvent() throws Exception {
        CommandExecutor executor = mock(CommandExecutor.class);
        AtomicReference<StoredEvent> received = new AtomicReference<>();

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) { received.set(event); }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", handler),
                executor,
                NO_OP_PUBLISHER);

        int count = dispatcher.handle("automation", List.of(createTestEvents().get(0)));

        assertThat(count).isEqualTo(1);
        assertThat(received.get()).isNotNull();
        assertThat(received.get().type()).isEqualTo("WalletOpened");
    }

    @Test
    @DisplayName("Should pass CommandExecutor to handler")
    void shouldPassCommandExecutorToHandler() throws Exception {
        CommandExecutor executor = mock(CommandExecutor.class);
        AtomicReference<CommandExecutor> receivedExecutor = new AtomicReference<>();

        AutomationHandler handler = new AutomationHandler() {
            @Override public String getAutomationName() { return "automation"; }
            @Override public Set<String> getEventTypes() { return Set.of("WalletOpened"); }
            @Override public void react(StoredEvent event, CommandExecutor ce) { receivedExecutor.set(ce); }
        };

        AutomationDispatcher dispatcher = new AutomationDispatcher(
                Map.of("automation", handler),
                executor,
                NO_OP_PUBLISHER);

        dispatcher.handle("automation", List.of(createTestEvents().get(0)));

        assertThat(receivedExecutor.get()).isSameAs(executor);
    }

    @Test
    @DisplayName("Should propagate exception from handler")
    void shouldPropagateExceptionFromHandler() {
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
                executor,
                NO_OP_PUBLISHER);

        assertThatThrownBy(() -> dispatcher.handle("automation", List.of(createTestEvents().get(0))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("handler error");
    }

    private List<StoredEvent> createTestEvents() {
        return List.of(
                new StoredEvent(
                        "WalletOpened",
                        List.of(new Tag("wallet_id", "wallet-1")),
                        "{\"wallet_id\":\"wallet-1\",\"owner\":\"Alice\"}".getBytes(),
                        "tx-1",
                        1L,
                        Instant.now()),
                new StoredEvent(
                        "DepositMade",
                        List.of(new Tag("wallet_id", "wallet-1")),
                        "{\"amount\":100}".getBytes(),
                        "tx-2",
                        2L,
                        Instant.now())
        );
    }
}
