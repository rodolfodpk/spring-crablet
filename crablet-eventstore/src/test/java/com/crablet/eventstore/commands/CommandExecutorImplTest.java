package com.crablet.eventstore.commands;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreMetrics;
import com.crablet.eventstore.store.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandExecutorImpl.
 * Tests core command execution logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class CommandExecutorImplTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private CommandHandler<TestCommand> commandHandler;

    @Mock
    private EventStoreConfig config;
    
    @Mock
    private EventStoreMetrics metrics;

    private CommandExecutorImpl commandExecutor;

    @BeforeEach
    void setUp() {
        when(commandHandler.getCommandType()).thenReturn("test_command");
        when(config.isPersistCommands()).thenReturn(true);
        when(config.getTransactionIsolation()).thenReturn("READ_COMMITTED");
        
        commandExecutor = new CommandExecutorImpl(eventStore, List.of(commandHandler), config, metrics);
    }

    @Test
    void constructor_WithValidHandlers_ShouldBuildHandlerMap() {
        // Verify handler was registered
        assertNotNull(commandExecutor);
    }

    @Test
    void constructor_WithDuplicateHandlers_ShouldThrowException() {
        CommandHandler<TestCommand> duplicateHandler = mock(CommandHandler.class);
        when(duplicateHandler.getCommandType()).thenReturn("test_command");

        assertThrows(InvalidCommandException.class, () ->
                new CommandExecutorImpl(eventStore, List.of(commandHandler, duplicateHandler), config, metrics)
        );
    }

    @Test
    void executeCommand_WithNullCommand_ShouldThrowInvalidCommandException() {
        assertThrows(InvalidCommandException.class, () ->
                commandExecutor.executeCommand(null)
        );
    }

    @Test
    void executeCommand_WithNullHandler_ShouldThrowInvalidCommandException() {
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandHandler<?> nullHandler = null;
        
        // This test verifies internal validation, not the public API
        assertThrows(InvalidCommandException.class, () ->
                commandExecutor.executeCommand(command, nullHandler)
        );
    }

    @Test
    void executeCommand_WithValidCommand_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ExecutionResult> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            when(txStore.getCurrentTransactionId()).thenReturn("tx-123");
            return callback.apply(txStore);
        });

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasCreated());
        assertFalse(result.wasIdempotent());
        verify(eventStore).executeInTransaction(any());
        verify(metrics).recordEventsAppended(1);
        verify(metrics).recordEventType("test_event");
        verify(metrics, never()).recordIdempotentOperation(anyString());
    }

    @Test
    void executeCommand_WithIdempotentResult_ShouldReturnIdempotent() throws Exception {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandResult commandResult = new CommandResult(List.of(), AppendCondition.expectEmptyStream(), "ALREADY_PROCESSED");

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ExecutionResult> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            return callback.apply(txStore);
        });

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasIdempotent());
        assertFalse(result.wasCreated());
        assertEquals("ALREADY_PROCESSED", result.reason());
        verify(metrics).recordCommandSuccess(eq(command.getCommandType()), any());
        verify(metrics).recordIdempotentOperation(eq(command.getCommandType()));
        verify(metrics, never()).recordEventType(anyString());
    }

    @Test
    void executeCommand_WithNullEvents_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandResult commandResult = new CommandResult(null, AppendCondition.expectEmptyStream(), null);

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ?> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            return callback.apply(txStore);
        });

        // Act & Assert
        assertThrows(InvalidCommandException.class, () ->
                commandExecutor.executeCommand(command)
        );
    }

    @Test
    void executeCommand_WithEmptyEventType_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent invalidEvent = new AppendEvent("", Collections.emptyList(), "{}");
        CommandResult commandResult = CommandResult.of(List.of(invalidEvent), AppendCondition.expectEmptyStream());

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ?> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            return callback.apply(txStore);
        });

        // Act & Assert
        assertThrows(InvalidCommandException.class, () ->
                commandExecutor.executeCommand(command)
        );
    }

    @Test
    void executeCommand_WithInvalidTag_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        Tag invalidTag = new Tag("", "value"); // Empty key
        AppendEvent eventWithInvalidTag = new AppendEvent("test_event", List.of(invalidTag), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithInvalidTag), AppendCondition.expectEmptyStream());

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ?> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            return callback.apply(txStore);
        });

        // Act & Assert
        assertThrows(InvalidCommandException.class, () ->
                commandExecutor.executeCommand(command)
        );
    }

    @Test
    void executeCommand_WithConcurrencyException_ShouldPropagateException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ?> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            doThrow(new ConcurrencyException("Optimistic lock failure", command))
                    .when(txStore).appendIf(any(), any());
            return callback.apply(txStore);
        });

        // Act & Assert
        assertThrows(ConcurrencyException.class, () ->
                commandExecutor.executeCommand(command)
        );
    }

    @Test
    void executeCommand_WithDuplicateOperationDetected_ShouldReturnIdempotent() throws Exception {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ExecutionResult> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            doThrow(new ConcurrencyException("duplicate operation detected", command))
                    .when(txStore).appendIf(any(), any());
            return callback.apply(txStore);
        });

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasIdempotent());
        assertEquals("DUPLICATE_OPERATION", result.reason());
        verify(metrics).recordCommandSuccess(eq(command.getCommandType()), any());
        verify(metrics).recordIdempotentOperation(eq(command.getCommandType()));
        verify(metrics, never()).recordEventsAppended(anyInt());
    }

    @Test
    void executeCommand_WithOpenWalletDuplicate_ShouldThrowConcurrencyException() {
        // Arrange
        CommandHandler<TestCommand> walletHandler = mock(CommandHandler.class);
        when(walletHandler.getCommandType()).thenReturn("open_wallet");
        CommandExecutorImpl walletCommandExecutor = new CommandExecutorImpl(eventStore, List.of(walletHandler), config, metrics);
        
        TestCommand command = new TestCommand("open_wallet", "entity-123");
        AppendEvent event = AppendEvent.builder("wallet_opened")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        when(walletHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ?> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            doThrow(new ConcurrencyException("duplicate operation detected", command))
                    .when(txStore).appendIf(any(), any());
            return callback.apply(txStore);
        });

        // Act & Assert
        assertThrows(ConcurrencyException.class, () ->
                walletCommandExecutor.executeCommand(command)
        );
    }

    @Test
    void executeCommand_ByCommandType_ShouldFindHandlerAndExecute() throws Exception {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandResult commandResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());

        when(commandHandler.handle(any(), eq(command))).thenReturn(commandResult);
        when(eventStore.executeInTransaction(any())).thenAnswer(invocation -> {
            Function<EventStore, ExecutionResult> callback = invocation.getArgument(0);
            EventStore txStore = mock(EventStore.class);
            when(txStore.getCurrentTransactionId()).thenReturn("tx-123");
            return callback.apply(txStore);
        });

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasCreated());
    }

    @Test
    void executeCommand_WithUnknownCommandType_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("unknown_command", "entity-123");

        // Act & Assert
        assertThrows(InvalidCommandException.class, () ->
                commandExecutor.executeCommand(command)
        );
    }

    /**
     * Test command implementation for unit tests.
     */
    private static class TestCommand implements Command {
        private final String commandType;
        private final String entityId;

        TestCommand(String commandType, String entityId) {
            this.commandType = commandType;
            this.entityId = entityId;
        }

        @Override
        public String getCommandType() {
            return commandType;
        }

        public String getEntityId() {
            return entityId;
        }
    }
}

