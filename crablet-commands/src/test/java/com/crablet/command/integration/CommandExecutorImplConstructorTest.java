package com.crablet.command.integration;

import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for CommandExecutorImpl constructor error paths.
 * Tests handler registration failures, duplicate handlers, and empty handlers.
 */
@DisplayName("CommandExecutorImpl Constructor Error Paths")
class CommandExecutorImplConstructorTest {

    private final EventStore eventStore = mock(EventStore.class);
    private final EventStoreConfig config = new EventStoreConfig();
    private final ClockProvider clock = new ClockProviderImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @Test
    @DisplayName("Constructor with handler that fails type extraction should throw IllegalStateException")
    void constructor_WithHandlerFailingTypeExtraction_ShouldThrowIllegalStateException() {
        // Given: A handler that doesn't properly implement CommandHandler
        // This handler will fail type extraction because it doesn't have @JsonSubTypes
        class InvalidHandler implements CommandHandler<Object> {
            @Override
            public CommandResult handle(EventStore eventStore, Object command) {
                return null;
            }
        }
        
        InvalidHandler invalidHandler = new InvalidHandler();
        List<CommandHandler<?>> handlers = List.of(invalidHandler);

        // When & Then - Should throw IllegalStateException (lines 112-116)
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, handlers, config, clock, objectMapper, eventPublisher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to extract command type from handler")
                .hasCauseInstanceOf(InvalidCommandException.class);
    }

    @Test
    @DisplayName("Constructor with duplicate handlers should throw InvalidCommandException")
    void constructor_WithDuplicateHandlers_ShouldThrowInvalidCommandException() {
        // Given: Two handlers for the same command type
        TestCommandHandler handler1 = new TestCommandHandler();
        TestCommandHandler handler2 = new TestCommandHandler();
        List<CommandHandler<?>> handlers = List.of(handler1, handler2);

        // When & Then - Should throw InvalidCommandException (lines 120-126)
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, handlers, config, clock, objectMapper, eventPublisher))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Duplicate handler for command type");
    }

    @Test
    @DisplayName("Constructor with empty handlers list should log warning but not throw")
    void constructor_WithEmptyHandlers_ShouldNotThrow() {
        // Given: Empty handlers list
        List<CommandHandler<?>> emptyHandlers = Collections.emptyList();

        // When - Should not throw, but log warning (lines 134-135)
        CommandExecutorImpl executor = new CommandExecutorImpl(
                eventStore, emptyHandlers, config, clock, objectMapper, eventPublisher);

        // Then - Executor is created but handlers map is empty
        assertNotNull(executor);
        // Note: The warning log is verified indirectly - if it throws, test fails
    }

    @Test
    @DisplayName("Constructor with null eventStore should throw IllegalArgumentException")
    void constructor_WithNullEventStore_ShouldThrowIllegalArgumentException() {
        // Given
        List<CommandHandler<?>> handlers = List.of(new TestCommandHandler());

        // When & Then
        assertThatThrownBy(() -> new CommandExecutorImpl(
                null, handlers, config, clock, objectMapper, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventStore must not be null");
    }

    @Test
    @DisplayName("Constructor with null handlers should throw IllegalArgumentException")
    void constructor_WithNullHandlers_ShouldThrowIllegalArgumentException() {
        // When & Then
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, null, config, clock, objectMapper, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commandHandlers must not be null");
    }

    @Test
    @DisplayName("Constructor with null config should throw IllegalArgumentException")
    void constructor_WithNullConfig_ShouldThrowIllegalArgumentException() {
        // Given
        List<CommandHandler<?>> handlers = List.of(new TestCommandHandler());

        // When & Then
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, handlers, null, clock, objectMapper, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config must not be null");
    }

    @Test
    @DisplayName("Constructor with null clock should throw IllegalArgumentException")
    void constructor_WithNullClock_ShouldThrowIllegalArgumentException() {
        // Given
        List<CommandHandler<?>> handlers = List.of(new TestCommandHandler());

        // When & Then
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, handlers, config, null, objectMapper, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clock must not be null");
    }

    @Test
    @DisplayName("Constructor with null objectMapper should throw IllegalArgumentException")
    void constructor_WithNullObjectMapper_ShouldThrowIllegalArgumentException() {
        // Given
        List<CommandHandler<?>> handlers = List.of(new TestCommandHandler());

        // When & Then
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, handlers, config, clock, null, eventPublisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectMapper must not be null");
    }

    @Test
    @DisplayName("Constructor with null eventPublisher should throw IllegalArgumentException")
    void constructor_WithNullEventPublisher_ShouldThrowIllegalArgumentException() {
        // Given
        List<CommandHandler<?>> handlers = List.of(new TestCommandHandler());

        // When & Then
        assertThatThrownBy(() -> new CommandExecutorImpl(
                eventStore, handlers, config, clock, objectMapper, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventPublisher must not be null");
    }
}

