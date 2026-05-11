package com.crablet.command.integration;

import com.crablet.command.CommandDecision;
import com.crablet.command.ExecutionResult;
import com.crablet.command.OnDuplicate;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for orthogonal command idempotency API.
 * Verifies that Commutative and NonCommutative decisions can carry an IdempotencyKey
 * and return idempotent results on duplicate execution.
 */
@DisplayName("Orthogonal Command Idempotency Integration Tests")
class CommandExecutorImplOrthogonalIdempotencyTest extends AbstractCommandTest {

    @BeforeEach
    void setUp() {
        TestCommandHandler.clearHandlerLogic();
    }

    // --- Commutative + idempotency ---

    @Test
    @DisplayName("commutative command with idempotency key returns idempotent on duplicate")
    void commutative_WithIdempotencyKey_ReturnsIdempotentOnDuplicate() {
        TestCommand command = new TestCommand("test_command", "entity-comm-1");
        AppendEvent event = AppendEvent.builder("transfer_made")
                .tag("entity_id", "entity-comm-1")
                .tag("transfer_id", "txn-001")
                .data("{}")
                .build();

        TestCommandHandler.setHandlerLogic(cmd ->
                CommandDecision.Commutative.of(event)
                        .idempotent("transfer_made", "transfer_id", "txn-001"));

        ExecutionResult first = commandExecutor.execute(command);
        assertThat(first.wasCreated()).isTrue();

        ExecutionResult second = commandExecutor.execute(command);
        assertThat(second.wasIdempotent()).isTrue();
        assertThat(second.reason()).isEqualTo("DUPLICATE_OPERATION");
    }

    @Test
    @DisplayName("commutative command with THROW idempotency policy throws on duplicate")
    void commutative_WithIdempotencyKeyThrow_ThrowsOnDuplicate() {
        TestCommand command = new TestCommand("test_command", "entity-comm-throw");
        AppendEvent event = AppendEvent.builder("unique_event")
                .tag("entity_id", "entity-comm-throw")
                .tag("op_id", "unique-op-888")
                .data("{}")
                .build();

        TestCommandHandler.setHandlerLogic(cmd ->
                CommandDecision.Commutative.of(event)
                        .idempotent("unique_event", "op_id", "unique-op-888", OnDuplicate.THROW));

        ExecutionResult first = commandExecutor.execute(command);
        assertThat(first.wasCreated()).isTrue();

        assertThatThrownBy(() -> commandExecutor.execute(command))
                .isInstanceOf(ConcurrencyException.class);
    }

    // --- Crash-recovery simulation ---

    @Test
    @DisplayName("crash-recovery: duplicate protection prevents second append when automation retries")
    void crashRecovery_DuplicateProtectionPreventsSecondAppend() {
        TestCommand command = new TestCommand("test_command", "entity-crash-1");
        AppendEvent event = AppendEvent.builder("notification_sent")
                .tag("entity_id", "entity-crash-1")
                .tag("notification_id", "notif-003")
                .data("{}")
                .build();

        TestCommandHandler.setHandlerLogic(cmd ->
                CommandDecision.Commutative.of(event)
                        .idempotent("notification_sent", "notification_id", "notif-003"));

        // First execution succeeds (simulates automation command execution)
        ExecutionResult first = commandExecutor.execute(command);
        assertThat(first.wasCreated()).isTrue();

        // Automation progress was NOT advanced (simulates crash before progress commit).
        // The automation retries the same event — the command is executed again.
        ExecutionResult retry = commandExecutor.execute(command);
        assertThat(retry.wasIdempotent()).isTrue();
        assertThat(retry.reason()).isEqualTo("DUPLICATE_OPERATION");
    }
}
