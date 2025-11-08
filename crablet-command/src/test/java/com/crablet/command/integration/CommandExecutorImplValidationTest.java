package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandResult;
import com.crablet.command.ExecutionResult;
import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for CommandExecutorImpl validation logic.
 * Tests validateCommandResult() method through public API.
 */
class CommandExecutorImplValidationTest extends AbstractCommandTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    void executeCommand_WithNullEvents_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandResult commandResult = new CommandResult(null, AppendCondition.expectEmptyStream(), null);

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("null events");
    }

    @Test
    void executeCommand_WithEventHavingNullType_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithNullType = new AppendEvent(null, Collections.emptyList(), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithNullType), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("empty type");
    }

    @Test
    void executeCommand_WithEventHavingEmptyType_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithEmptyType = new AppendEvent("", Collections.emptyList(), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithEmptyType), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("empty type");
    }

    @Test
    void executeCommand_WithEventHavingNullTagKey_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithNullTagKey = new AppendEvent("test_event", 
            List.of(new Tag(null, "value")), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithNullTagKey), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag key");
    }

    @Test
    void executeCommand_WithEventHavingEmptyTagKey_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithEmptyTagKey = new AppendEvent("test_event", 
            List.of(new Tag("", "value")), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithEmptyTagKey), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag key");
    }

    @Test
    void executeCommand_WithEventHavingNullTagValue_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithNullTagValue = new AppendEvent("test_event", 
            List.of(new Tag("key", null)), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithNullTagValue), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Empty tag value");
    }

    @Test
    void executeCommand_WithEventHavingEmptyTagValue_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent eventWithEmptyTagValue = new AppendEvent("test_event", 
            List.of(new Tag("key", "")), "{}");
        CommandResult commandResult = CommandResult.of(List.of(eventWithEmptyTagValue), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act & Assert
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
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
        CommandResult commandResult = CommandResult.of(List.of(validEvent), AppendCondition.expectEmptyStream());

        TestCommandHandler.setHandlerLogic(cmd -> commandResult);

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasCreated());
    }
}

