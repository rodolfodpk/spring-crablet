package com.crablet.command.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.command.CommandResult;
import com.crablet.command.ExecutionResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for CommandExecutorImpl concurrency exception handling.
 * Tests handleConcurrencyException() method through public API.
 */
class CommandExecutorImplConcurrencyTest extends AbstractCommandTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    @Test
    void executeCommand_WithNonIdempotentConcurrencyException_ShouldThrowException() {
        // Arrange - trigger a cursor-based conflict (not idempotent)
        TestCommand command = new TestCommand("test_command", "entity-123");
        AppendEvent event = AppendEvent.builder("test_event")
                .tag("entityId", "entity-123")
                .data("{}")
                .build();
        
        // First execution to establish a cursor
        CommandResult firstResult = CommandResult.of(List.of(event), AppendCondition.expectEmptyStream());
        TestCommandHandler.setHandlerLogic(cmd -> firstResult);
        ExecutionResult first = commandExecutor.executeCommand(command);
        assertTrue(first.wasCreated());
        
        // Second execution with stale cursor - should trigger ConcurrencyException
        // but NOT "duplicate operation detected" (it's a cursor conflict, not idempotency)
        Query decisionModel = Query.forEventAndTag("test_event", "entityId", "entity-123");
        Cursor staleCursor = Cursor.of(0L, Instant.now(), "old-tx-id");
        AppendCondition conditionWithStaleCursor = new AppendConditionBuilder(decisionModel, staleCursor).build();
        
        CommandResult secondResult = CommandResult.of(List.of(event), conditionWithStaleCursor);
        TestCommandHandler.setHandlerLogic(cmd -> secondResult);

        // Act & Assert - should throw ConcurrencyException (not idempotent)
        assertThatThrownBy(() -> commandExecutor.executeCommand(command))
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
        AppendCondition idempotencyCondition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                .withIdempotencyCheck("test_event", "operation_id", "op-123")
                .build();
        CommandResult firstResult = CommandResult.of(List.of(event), idempotencyCondition);
        TestCommandHandler.setHandlerLogic(cmd -> firstResult);
        
        ExecutionResult first = commandExecutor.executeCommand(command);
        assertTrue(first.wasCreated());
        
        // Second execution with same operation_id - should be idempotent
        // The idempotency check will detect the duplicate and return "duplicate operation detected"
        CommandResult secondResult = CommandResult.of(List.of(event), idempotencyCondition);
        TestCommandHandler.setHandlerLogic(cmd -> secondResult);

        // Act
        ExecutionResult second = commandExecutor.executeCommand(command);

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
        ExecutionResult first = commandExecutor.executeCommand(firstCommand);
        assertTrue(first.wasCreated());
        
        // Second execution with same wallet_id - should throw (not idempotent for open_wallet)
        com.crablet.examples.wallet.commands.OpenWalletCommand secondCommand = 
            com.crablet.examples.wallet.commands.OpenWalletCommand.of("wallet-123", "Bob", 2000);

        // Act & Assert - should throw ConcurrencyException (open_wallet always throws on duplicate)
        assertThatThrownBy(() -> commandExecutor.executeCommand(secondCommand))
                .isInstanceOf(ConcurrencyException.class);
    }
}

