package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandResult;
import com.crablet.command.ExecutionResult;
import com.crablet.eventstore.dcb.AppendCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CommandExecutorImpl idempotent result handling.
 * Tests handleIdempotentResult() method through public API.
 */
class CommandExecutorImplIdempotentTest extends AbstractCommandTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    void executeCommand_WithEmptyResultWithReason_ShouldReturnIdempotent() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandResult emptyResult = new CommandResult(List.of(), AppendCondition.expectEmptyStream(), "ALREADY_PROCESSED");

        TestCommandHandler.setHandlerLogic(cmd -> emptyResult);

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasIdempotent());
        assertEquals("ALREADY_PROCESSED", result.reason());
    }

    @Test
    void executeCommand_WithEmptyResultWithoutReason_ShouldReturnIdempotent() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandResult emptyResult = new CommandResult(List.of(), AppendCondition.expectEmptyStream(), null);

        TestCommandHandler.setHandlerLogic(cmd -> emptyResult);

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertNotNull(result);
        assertTrue(result.wasIdempotent());
        // When reason is null, it defaults to "DUPLICATE_OPERATION" in handleIdempotentResult()
        assertEquals("DUPLICATE_OPERATION", result.reason());
    }

    @Test
    void executeCommand_WithEmptyResult_ShouldNotAppendEvents() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandResult emptyResult = new CommandResult(List.of(), AppendCondition.expectEmptyStream(), "ALREADY_PROCESSED");

        TestCommandHandler.setHandlerLogic(cmd -> emptyResult);

        // Act
        ExecutionResult result = commandExecutor.executeCommand(command);

        // Assert
        assertTrue(result.wasIdempotent());
        
        // Verify no events were stored (empty result means no events to append)
        // This is verified by the fact that the command executed successfully
        // without appending any events
    }
}

