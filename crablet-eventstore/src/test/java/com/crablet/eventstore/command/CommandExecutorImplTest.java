package com.crablet.eventstore.command;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.EventStoreConfig;
import com.crablet.eventstore.store.EventStoreMetrics;
import com.crablet.eventstore.store.Tag;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private CommandHandler<TestCommand> commandHandler;

    @Mock
    private EventStoreConfig config;
    
    @Mock
    private EventStoreMetrics metrics;
    
    private ObjectMapper objectMapper;

    private CommandExecutorImpl commandExecutor;

    @BeforeEach
    void setUp() {
        when(config.isPersistCommands()).thenReturn(true);
        when(config.getTransactionIsolation()).thenReturn("READ_COMMITTED");
        
        // Create a real handler instance so CommandTypeResolver can extract the generic type
        // Use a spy so we can still mock handle() method in tests
        CommandHandler<TestCommand> handler = new CommandHandler<TestCommand>() {
            @Override
            public CommandResult handle(EventStore eventStore, TestCommand command) {
                return null; // Will be mocked in individual tests
            }
        };
        commandHandler = spy(handler);
        
        // Create ObjectMapper for command serialization
        objectMapper = new ObjectMapper();
        
        commandExecutor = new CommandExecutorImpl(eventStore, List.of(commandHandler), config, metrics, objectMapper);
    }

    @Test
    void constructor_WithValidHandlers_ShouldBuildHandlerMap() {
        // Verify handler was registered
        assertNotNull(commandExecutor);
    }

    @Test
    void constructor_WithDuplicateHandlers_ShouldThrowException() {
        // Create a real handler that will resolve to the same command type
        CommandHandler<TestCommand> duplicateHandler = new CommandHandler<TestCommand>() {
            @Override
            public CommandResult handle(EventStore eventStore, TestCommand command) {
                return null;
            }
        };

        ObjectMapper mapper = new ObjectMapper();
        assertThrows(InvalidCommandException.class, () ->
                new CommandExecutorImpl(eventStore, List.of(commandHandler, duplicateHandler), config, metrics, mapper)
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
        CommandHandler<TestCommand> nullHandler = null;
        
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
        verify(metrics).recordCommandSuccess(eq(command.commandType()), any());
        verify(metrics).recordIdempotentOperation(eq(command.commandType()));
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
        verify(metrics).recordCommandSuccess(eq(command.commandType()), any());
        verify(metrics).recordIdempotentOperation(eq(command.commandType()));
        verify(metrics, never()).recordEventsAppended(anyInt());
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
     * Test command interface for unit tests.
     * Provides @JsonSubTypes annotation for CommandTypeResolver.
     */
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "commandType"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TestCommand.class, name = "test_command")
    })
    private interface TestCommandInterface {
    }

    /**
     * Test command implementation for unit tests.
     * Simple record with commandType field for Jackson serialization.
     */
    private record TestCommand(
            @JsonProperty("commandType") String commandType,
            @JsonProperty("entityId") String entityId
    ) implements TestCommandInterface {
    }
}

