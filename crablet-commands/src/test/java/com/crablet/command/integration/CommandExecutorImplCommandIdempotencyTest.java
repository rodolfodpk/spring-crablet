package com.crablet.command.integration;

import com.crablet.command.CommandDecision;
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
    @DisplayName("First execution with idempotency key succeeds and is not idempotent")
    void firstExecutionSucceeds() {
        String key = "test-op:" + UUID.randomUUID();
        ExecutionResult result = commandExecutor.execute(new TestCommand("test_command", "e-1"), key);

        assertThat(result.wasIdempotent()).isFalse();
    }

    @Test
    @DisplayName("Duplicate submission with same key is detected pre-handler and returns idempotent")
    void duplicateKeyIsDetectedPreHandler() {
        String key = "test-op:" + UUID.randomUUID();
        AtomicInteger handlerCallCount = new AtomicInteger(0);
        TestCommandHandler.setHandlerLogic(cmd -> {
            handlerCallCount.incrementAndGet();
            return CommandDecision.Commutative.of(
                AppendEvent.builder("TestEventHappened")
                    .tag("entity_id", ((TestCommand) cmd).entityId())
                    .data("{}")
                    .build());
        });

        commandExecutor.execute(new TestCommand("test_command", "e-1"), key);
        ExecutionResult second = commandExecutor.execute(new TestCommand("test_command", "e-1"), key);

        assertThat(second.wasIdempotent()).isTrue();
        assertThat(second.reason()).isEqualTo("COMMAND_DUPLICATE");
        assertThat(handlerCallCount.get()).as("handler must not run on duplicate").isEqualTo(1);
    }

    @Test
    @DisplayName("Different keys on the same command type do not interfere")
    void differentKeysDoNotInterfere() {
        String key1 = "test-op:" + UUID.randomUUID();
        String key2 = "test-op:" + UUID.randomUUID();

        ExecutionResult r1 = commandExecutor.execute(new TestCommand("test_command", "e-1"), key1);
        ExecutionResult r2 = commandExecutor.execute(new TestCommand("test_command", "e-2"), key2);

        assertThat(r1.wasIdempotent()).isFalse();
        assertThat(r2.wasIdempotent()).isFalse();
    }

    @Test
    @DisplayName("Null idempotency key falls through to normal execution")
    void nullKeyExecutesNormally() {
        ExecutionResult result = commandExecutor.execute(
            new TestCommand("test_command", "e-1"), (String) null);

        assertThat(result.wasIdempotent()).isFalse();
    }

    @Test
    @DisplayName("Idempotency key is independent of correlation ID")
    void idempotencyKeyWithCorrelationId() {
        String key = "test-op:" + UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        ExecutionResult first = commandExecutor.execute(
            new TestCommand("test_command", "e-1"), correlationId, key);
        ExecutionResult second = commandExecutor.execute(
            new TestCommand("test_command", "e-1"), correlationId, key);

        assertThat(first.wasIdempotent()).isFalse();
        assertThat(second.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Rollback releases the key so the next attempt proceeds as new")
    void rollbackReleasesKey() {
        String key = "test-op:" + UUID.randomUUID();

        TestCommandHandler.setHandlerLogic(cmd -> { throw new RuntimeException("simulated failure"); });

        assertThatThrownBy(() -> commandExecutor.execute(new TestCommand("test_command", "e-1"), key))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("simulated failure");

        TestCommandHandler.setHandlerLogic(cmd ->
            CommandDecision.Commutative.of(
                AppendEvent.builder("TestEventHappened")
                    .tag("entity_id", "e-1")
                    .data("{}")
                    .build()));

        ExecutionResult retry = commandExecutor.execute(new TestCommand("test_command", "e-1"), key);
        assertThat(retry.wasIdempotent())
            .as("key must be released by rollback so retry is treated as new")
            .isFalse();
    }
}
