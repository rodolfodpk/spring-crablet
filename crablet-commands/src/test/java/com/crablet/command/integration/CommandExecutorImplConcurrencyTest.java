package com.crablet.command.integration;

import com.crablet.command.CommandDecision;
import com.crablet.command.ExecutionResult;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.DCBErrorCode;
import com.crablet.eventstore.DCBViolation;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for CommandExecutorImpl concurrency exception handling.
 * Tests handleConcurrencyException() method through public API.
 */
class CommandExecutorImplConcurrencyTest extends AbstractCommandTest {

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    void executeCommand_WithNonIdempotentConcurrencyException_ShouldThrowException() {
        // Arrange - trigger a stream-position-based conflict (not idempotent)
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        
        // First execution to establish a stream position
        CommandDecision firstResult = CommandDecision.Commutative.of(event);
        TestCommandHandler.setHandlerLogic(cmd -> firstResult);
        ExecutionResult first = commandExecutor.execute(command);
        assertTrue(first.wasCreated());

        // Second execution with stale stream position - should trigger ConcurrencyException
        // but NOT "duplicate operation detected" (it's a streamPosition conflict, not idempotency)
        Query decisionModel = Query.forEventAndTag("test_event", "entityId", "entity-123");
        StreamPosition staleStreamPosition = StreamPosition.of(0L, Instant.now(), "old-tx-id");

        CommandDecision secondResult = new CommandDecision.NonCommutative(List.of(event), decisionModel, staleStreamPosition);
        TestCommandHandler.setHandlerLogic(cmd -> secondResult);

        // Act & Assert - should throw ConcurrencyException (not idempotent)
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(ConcurrencyException.class);
    }

    @Test
    void executeCommand_WithIdempotentDuplicate_ShouldReturnIdempotent() {
        // Arrange - create an idempotency scenario using operation_id tag
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .tag("operation_id", "op-123") // Idempotency tag
                .data("{}")
                .build();
        
        // First execution - should succeed
        CommandDecision firstResult = new CommandDecision.Idempotent(
                List.of(event), "test_event", "operation_id", "op-123");
        TestCommandHandler.setHandlerLogic(cmd -> firstResult);

        ExecutionResult first = commandExecutor.execute(command);
        assertTrue(first.wasCreated());

        // Second execution with same operation_id - should be idempotent
        // The idempotency check will detect the duplicate and return "duplicate operation detected"
        CommandDecision secondResult = new CommandDecision.Idempotent(
                List.of(event), "test_event", "operation_id", "op-123");
        TestCommandHandler.setHandlerLogic(cmd -> secondResult);

        // Act
        ExecutionResult second = commandExecutor.execute(command);

        // Assert - should return idempotent result (not throw)
        assertTrue(second.wasIdempotent());
        assertEquals("DUPLICATE_OPERATION", second.reason());
    }

    @Test
    void executeCommand_WithOpenWalletDuplicate_ShouldThrowException() {
        // Arrange - open_wallet duplicates should always throw, never return idempotent
        // This tests the special case in handleConcurrencyException for "open_wallet"
        com.crablet.examples.wallet.commands.OpenWalletCommand firstCommand = 
            com.crablet.examples.wallet.commands.OpenWalletCommand.of("wallet-123", "Alice", 1000);
        
        // First execution succeeds
        ExecutionResult first = commandExecutor.execute(firstCommand);
        assertTrue(first.wasCreated());
        
        // Second execution with same wallet_id - should throw (not idempotent for open_wallet)
        com.crablet.examples.wallet.commands.OpenWalletCommand secondCommand = 
            com.crablet.examples.wallet.commands.OpenWalletCommand.of("wallet-123", "Bob", 2000);

        // Act & Assert - should throw ConcurrencyException (open_wallet always throws on duplicate)
        assertThatThrownBy(() -> commandExecutor.execute(secondCommand))
                .isInstanceOf(ConcurrencyException.class);
    }

    @Test
    void executeCommand_WithCommutativeGuarded_StaggeredLifecycleConflict_ThrowsGuardViolation() {
        // Arrange - capture the guard position before a lifecycle event (unrelated type to the
        // appended event, per CommutativeGuarded's own overlap validation) commits.
        String entityId = "guard-entity-" + UUID.randomUUID();
        Query lifecycleQuery = Query.forEventAndTag("entity_closed", "entityId", entityId);
        StreamPosition guardPosition = eventStore.project(
                lifecycleQuery, StreamPosition.zero(), StateProjector.exists()).streamPosition();

        // Staggered: the lifecycle event fully commits after the guard position was captured
        // but before the guarded append runs.
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("entity_closed").tag("entityId", entityId).data("{}").build()));

        AppendEvent guardedEvent = AppendEvent.builder("test_event").tag("entityId", entityId).data("{}").build();
        CommandDecision.CommutativeGuarded decision =
                CommandDecision.CommutativeGuarded.withLifecycleGuard(guardedEvent, lifecycleQuery, guardPosition);
        TestCommandHandler.setHandlerLogic(cmd -> decision);

        TestCommand command = new TestCommand("test_command", entityId);
        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(ConcurrencyException.class)
                .satisfies(ex -> {
                    DCBViolation violation = ((ConcurrencyException) ex).violation;
                    assertThat(violation).isNotNull();
                    assertThat(violation.errorCode()).isEqualTo(DCBErrorCode.GUARD_VIOLATION);
                });
    }

    @Test
    void executeCommand_WithCommutativeGuarded_IdempotentRetryAfterLifecycleChange_ReturnsIdempotent() {
        // Arrange - idempotency must be checked before the guard, so a retry with the same
        // idempotency key against a since-closed entity returns the idempotent result rather
        // than throwing GUARD_VIOLATION.
        String entityId = "guard-entity-" + UUID.randomUUID();
        Query lifecycleQuery = Query.forEventAndTag("entity_closed", "entityId", entityId);
        StreamPosition guardPosition = eventStore.project(
                lifecycleQuery, StreamPosition.zero(), StateProjector.exists()).streamPosition();

        AppendEvent guardedEvent = AppendEvent.builder("test_event")
                .tag("entityId", entityId).tag("op_id", "op-1").data("{}").build();
        CommandDecision.CommutativeGuarded firstDecision = CommandDecision.CommutativeGuarded
                .withLifecycleGuard(guardedEvent, lifecycleQuery, guardPosition)
                .idempotent("test_event", "op_id", "op-1");
        TestCommandHandler.setHandlerLogic(cmd -> firstDecision);

        TestCommand command = new TestCommand("test_command", entityId);
        ExecutionResult first = commandExecutor.execute(command);
        assertTrue(first.wasCreated());

        // The lifecycle change commits after the first successful execution.
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("entity_closed").tag("entityId", entityId).data("{}").build()));

        // Retry with the same stale guardPosition and idempotencyKey - should return idempotent,
        // not throw GUARD_VIOLATION, since idempotency is checked before concurrency.
        CommandDecision.CommutativeGuarded retryDecision = CommandDecision.CommutativeGuarded
                .withLifecycleGuard(guardedEvent, lifecycleQuery, guardPosition)
                .idempotent("test_event", "op_id", "op-1");
        TestCommandHandler.setHandlerLogic(cmd -> retryDecision);

        ExecutionResult retry = commandExecutor.execute(command);
        assertTrue(retry.wasIdempotent());
    }
}

