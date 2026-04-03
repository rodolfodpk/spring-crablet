package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandExecutorImpl;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.command.InvalidCommandException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CommandExecutorImpl error handling paths.
 * Tests uncovered error scenarios: null handler (2-arg), empty commandType.
 * 
 * Note: JsonProcessingException and Exception wrapping scenarios are difficult to test
 * without mocking ObjectMapper or creating complex serialization failures. These paths
 * are edge cases that would require significant test infrastructure changes.
 */
@DisplayName("CommandExecutorImpl Error Handling Tests")
class CommandExecutorImplErrorHandlingTest extends AbstractCommandTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    @DisplayName("execute(command, null) should throw InvalidCommandException")
    void execute_WithNullHandler_ShouldThrowInvalidCommandException() {
        // Arrange
        TestCommand command = new TestCommand("test_command", "entity-123");
        CommandHandler<TestCommand> nullHandler = null;

        // Act & Assert - use execute() method which takes handler parameter
        assertThatThrownBy(() -> commandExecutor.execute(command, nullHandler))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Handler cannot be null");
    }

    @Test
    @DisplayName("executeCommand(command, handler) with empty commandType should throw InvalidCommandException")
    void executeCommand_WithEmptyCommandType_ShouldThrowInvalidCommandException() {
        // Arrange - command with empty string commandType
        TestCommand command = new TestCommand("", "entity-123");
        TestCommandHandler handler = new TestCommandHandler();
        TestCommandHandler.setHandlerLogic(cmd -> CommandResult.empty());

        // Act & Assert - use 2-arg version to test the empty commandType validation (line 205-209)
        assertThat(commandExecutor).isInstanceOf(CommandExecutorImpl.class);
        CommandExecutorImpl impl = (CommandExecutorImpl) commandExecutor;
        
        assertThatThrownBy(() -> impl.executeCommand(command, handler))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("Command type is null or empty");
    }
}

