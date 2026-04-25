package com.crablet.command.integration;

import com.crablet.command.CommandDecision;
import com.crablet.command.ExecutionResult;
import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for CommandExecutorImpl validation logic.
 * Tests validateCommandDecision() method through public API.
 */
class CommandExecutorImplValidationTest extends AbstractCommandTest {

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    void executeCommand_WithNullEvents_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandDecision commandResult = new CommandDecision.Commutative(null);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("null events");
    }

    @Test
    void executeCommand_WithEventHavingNullType_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithNullType = new AppendEvent(null, Collections.emptyList(), "{}");
        CommandDecision commandResult = CommandDecision.Commutative.of(eventWithNullType);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("empty type");
    }

    @Test
    void executeCommand_WithEventHavingEmptyType_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithEmptyType = new AppendEvent("", Collections.emptyList(), "{}");
        CommandDecision commandResult = CommandDecision.Commutative.of(eventWithEmptyType);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("empty type");
    }

    @Test
    void executeCommand_WithEventHavingNullTagKey_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithNullTagKey = new AppendEvent("test_event", 
            List.of(new Tag(null, "value")), "{}");
        CommandDecision commandResult = CommandDecision.Commutative.of(eventWithNullTagKey);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag key");
    }

    @Test
    void executeCommand_WithEventHavingEmptyTagKey_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithEmptyTagKey = new AppendEvent("test_event", 
            List.of(new Tag("", "value")), "{}");
        CommandDecision commandResult = CommandDecision.Commutative.of(eventWithEmptyTagKey);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag key");
    }

    @Test
    void executeCommand_WithEventHavingNullTagValue_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithNullTagValue = new AppendEvent("test_event", 
            List.of(new Tag("key", null)), "{}");
        CommandDecision commandResult = CommandDecision.Commutative.of(eventWithNullTagValue);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag value");
    }

    @Test
    void executeCommand_WithEventHavingEmptyTagValue_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithEmptyTagValue = new AppendEvent("test_event", 
            List.of(new Tag("key", "")), "{}");
        CommandDecision commandResult = CommandDecision.Commutative.of(eventWithEmptyTagValue);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag value");
    }

    @Test
    void executeCommand_WithValidEvents_ShouldSucceed() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent validEvent = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        CommandDecision commandResult = CommandDecision.Commutative.of(validEvent);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act
        ExecutionResult result = commandExecutor.execute(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasCreated());
    }
}

