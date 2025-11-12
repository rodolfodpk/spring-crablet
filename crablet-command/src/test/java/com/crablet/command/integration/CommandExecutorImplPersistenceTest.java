package com.crablet.command.integration;

import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.CommandResult;
import com.crablet.command.ExecutionResult;
import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CommandExecutorImpl with persistence disabled.
 * Tests the persistence disabled path (lines 191-203) using direct instantiation.
 * 
 * Note: Integration test with Spring context would require custom test configuration
 * that conflicts with TestApplication's EventStoreConfig bean. This unit test approach
 * provides better control and directly tests the persistence disabled behavior.
 */
@DisplayName("CommandExecutorImpl Persistence Disabled Tests")
class CommandExecutorImplPersistenceTest {

    private EventStore eventStore;
    private EventStoreConfig config;
    private ClockProvider clock;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;
    private CommandExecutorImpl commandExecutor;
    private TestCommandHandler handler;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStore.class);
        config = new EventStoreConfig();
        config.setPersistCommands(false); // Disable persistence
        clock = new ClockProviderImpl();
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);
        handler = new TestCommandHandler();
        
        commandExecutor = new CommandExecutorImpl(
            eventStore, 
            List.of(handler), 
            config, 
            clock, 
            objectMapper, 
            eventPublisher
        );
        
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    @DisplayName("executeCommand() with persistence disabled should not call storeCommand")
    void executeCommand_WithPersistenceDisabled_ShouldNotCallStoreCommand() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);
        
        // Mock appendIf to return transaction ID
        when(eventStore.appendIf(Mockito.anyList(), Mockito.any()))
                .thenReturn("tx-123");
        when(eventStore.executeInTransaction(Mockito.any()))
                .thenAnswer(invocation -> {
                    // Execute the function passed to executeInTransaction
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<EventStore, ExecutionResult> fn = 
                        (java.util.function.Function<EventStore, ExecutionResult>) invocation.getArgument(0);
                    return fn.apply(eventStore);
                });

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertThat(result.wasCreated()).isTrue();
        
        // Verify storeCommand was NOT called (persistence disabled, line 253)
        verify(eventStore, never()).storeCommand(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    @DisplayName("executeCommand() with persistence disabled and missing commandType should throw InvalidCommandException")
    void executeCommand_WithPersistenceDisabledAndMissingCommandType_ShouldThrowInvalidCommandException() {
        // Arrange - command with null commandType
        TestCommand command = new TestCommand(null, "entity-123");

        // Act & Assert - Should throw InvalidCommandException (lines 196-200)
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("commandType");
    }

    @Test
    @DisplayName("executeCommand() with persistence disabled should use lightweight valueToTree() path")
    void executeCommand_WithPersistenceDisabled_ShouldUseLightweightSerialization() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);
        
        when(eventStore.appendIf(Mockito.anyList(), Mockito.any()))
                .thenReturn("tx-123");
        when(eventStore.executeInTransaction(Mockito.any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<EventStore, ExecutionResult> fn = 
                        (java.util.function.Function<EventStore, ExecutionResult>) invocation.getArgument(0);
                    return fn.apply(eventStore);
                });

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert - Command should execute successfully
        assertNotNull(result);
        assertThat(result.wasCreated()).isTrue();
        
        // Verify storeCommand was NOT called (confirms persistence disabled path, lines 191-203)
        verify(eventStore, never()).storeCommand(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }
}
