package com.crablet.command.integration;

import com.crablet.command.CommandDecision;
import com.crablet.command.CommandExecutionOptions;
import com.crablet.command.ExecutionResult;
import com.crablet.eventstore.AppendEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Command-level idempotency tests")
class CommandExecutorImplCommandIdempotencyTest extends AbstractCommandTest {

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
        TestCommandHandler.setHandlerLogic(cmd ->
            CommandDecision.Commutative.of(
                AppendEvent.builder("TestEventHappened")
                    .tag("entity_id", ((TestCommand) cmd).entityId())
                    .data("{}")
                    .build()));
    }

    @Test
    @DisplayName("First execution with command ID succeeds and is not idempotent")
    void firstExecutionSucceeds() {
        UUID commandId = UUID.randomUUID();
        ExecutionResult result = commandExecutor.execute(
            new TestCommand("test_command", "e-1"),
            CommandExecutionOptions.builder().commandId(commandId).build());

        assertThat(result.wasIdempotent()).isFalse();
    }

    @Test
    @DisplayName("Duplicate submission with same command ID is detected pre-handler and returns idempotent")
    void duplicateCommandIdIsDetectedPreHandler() {
        UUID commandId = UUID.randomUUID();
        CommandExecutionOptions options = CommandExecutionOptions.builder().commandId(commandId).build();
        AtomicInteger handlerCallCount = new AtomicInteger(0);
        TestCommandHandler.setHandlerLogic(cmd -> {
            handlerCallCount.incrementAndGet();
            return CommandDecision.Commutative.of(
                AppendEvent.builder("TestEventHappened")
                    .tag("entity_id", ((TestCommand) cmd).entityId())
                    .data("{}")
                    .build());
        });

        commandExecutor.execute(new TestCommand("test_command", "e-1"), options);
        ExecutionResult second = commandExecutor.execute(new TestCommand("test_command", "e-1"), options);

        assertThat(second.wasIdempotent()).isTrue();
        assertThat(second.reason()).isEqualTo("COMMAND_DUPLICATE");
        assertThat(handlerCallCount.get()).as("handler must not run on duplicate").isEqualTo(1);
    }

    @Test
    @DisplayName("Different command IDs on the same command type do not interfere")
    void differentCommandIdsDoNotInterfere() {
        ExecutionResult r1 = commandExecutor.execute(
            new TestCommand("test_command", "e-1"),
            CommandExecutionOptions.builder().commandId(UUID.randomUUID()).build());
        ExecutionResult r2 = commandExecutor.execute(
            new TestCommand("test_command", "e-2"),
            CommandExecutionOptions.builder().commandId(UUID.randomUUID()).build());

        assertThat(r1.wasIdempotent()).isFalse();
        assertThat(r2.wasIdempotent()).isFalse();
    }

    @Test
    @DisplayName("Command ID is independent of correlation ID")
    void commandIdWithCorrelationId() {
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        CommandExecutionOptions options = CommandExecutionOptions.builder()
            .correlationId(correlationId)
            .commandId(commandId)
            .build();

        ExecutionResult first = commandExecutor.execute(new TestCommand("test_command", "e-1"), options);
        ExecutionResult second = commandExecutor.execute(new TestCommand("test_command", "e-1"), options);

        assertThat(first.wasIdempotent()).isFalse();
        assertThat(second.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Rollback releases the command ID so the next attempt proceeds as new")
    void rollbackReleasesCommandId() {
        UUID commandId = UUID.randomUUID();
        CommandExecutionOptions options = CommandExecutionOptions.builder().commandId(commandId).build();

        TestCommandHandler.setHandlerLogic(cmd -> { throw new RuntimeException("simulated failure"); });

        assertThatThrownBy(() -> commandExecutor.execute(new TestCommand("test_command", "e-1"), options))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("simulated failure");

        TestCommandHandler.setHandlerLogic(cmd ->
            CommandDecision.Commutative.of(
                AppendEvent.builder("TestEventHappened")
                    .tag("entity_id", "e-1")
                    .data("{}")
                    .build()));

        ExecutionResult retry = commandExecutor.execute(new TestCommand("test_command", "e-1"), options);
        assertThat(retry.wasIdempotent())
            .as("command ID must be released by rollback so retry is treated as new")
            .isFalse();
    }
}
