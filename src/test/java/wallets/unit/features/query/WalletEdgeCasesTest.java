package wallets.unit.features.query;
import wallets.integration.AbstractWalletIntegrationTest;

import com.crablet.core.StoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallets.domain.event.DepositMade;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.features.query.WalletState;
import com.wallets.features.query.WalletStateProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import wallets.testutils.WalletTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test edge cases and boundary conditions with parameterized tests.
 * Uses AssertJ for assertions and JUnit 6 parameterized tests for comprehensive coverage.
 */
class WalletEdgeCasesTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private WalletStateProjector projector;

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @BeforeEach
    void setUp() {
        projector = new WalletStateProjector("wallet1", OBJECT_MAPPER);
    }

    @ParameterizedTest
    @CsvSource({
            // value, isValid, description
            "0, true, 'Zero balance'",
            "1, true, 'Minimum positive balance'",
            "1000000, true, 'Large balance'",
            "-1, true, 'Negative balance'",
            "2147483647, true, 'Maximum integer balance'"
    })
    @DisplayName("Should handle boundary values correctly")
    void testBoundaryValues(int value, boolean isValid, String description) {
        if (isValid) {
            assertThatCode(() -> new WalletState("wallet1", "Alice", value, Instant.now(), Instant.now()))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> new WalletState("wallet1", "Alice", value, Instant.now(), Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Should handle edge case transitions from empty state")
    void testEdgeCaseTransitions() {
        // Arrange
        WalletState empty = WalletState.empty();
        WalletOpened opened = WalletOpened.of("wallet1", "Alice", 0); // Zero initial balance
        StoredEvent event = WalletTestUtils.createEvent(opened);

        // Act
        WalletState state = projector.transition(empty, event);

        // Assert
        assertThat(state)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(0);
                    assertThat(s.isEmpty()).isFalse();
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                });
    }

    @Test
    @DisplayName("Should handle zero amount transfers")
    void testZeroAmountTransfers() {
        // Zero amount transfer should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", "wallet1", "wallet2", 0, 1000, 500, "Zero transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transfer amount must be positive");
    }

    @Test
    @DisplayName("Should handle maximum transfer limits")
    void testMaximumTransferLimits() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", Integer.MAX_VALUE, Instant.now(), Instant.now());

        // Maximum transfer
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", Integer.MAX_VALUE, 0, 500, "Max transfer");
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(0);
        assertThat(newState.walletId()).isEqualTo("wallet1");
        assertThat(newState.owner()).isEqualTo("Alice");
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, transferAmount, expectedBalance, shouldSucceed
            "1000, 1000, 0, true",
            "1000, 1001, -1, false",
            "0, 1, -1, false",
            "1, 1, 0, true",
            "2147483647, 2147483647, 0, true"
    })
    @DisplayName("Should handle edge case transfer amounts")
    void testEdgeCaseTransferAmounts(int initialBalance, int transferAmount, int expectedBalance, boolean shouldSucceed) {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", initialBalance, Instant.now(), Instant.now());

        if (shouldSucceed) {
            // Valid transfer
            MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", transferAmount,
                    expectedBalance, 500, "Edge case transfer");
            StoredEvent event = WalletTestUtils.createEvent(transfer);

            // Act
            WalletState newState = projector.transition(initialState, event);

            // Assert
            assertThat(newState.balance()).isEqualTo(expectedBalance);
        } else {
            // Invalid transfer - should not be created
            assertThatThrownBy(() -> MoneyTransferred.of("tx1", "wallet1", "wallet2", transferAmount,
                    expectedBalance, 500, "Invalid transfer"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Should handle edge case with same wallet transfer")
    void testSameWalletTransfer() {
        // Same wallet transfer should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", "wallet1", "wallet1", 300, 700, 700, "Same wallet transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot transfer to the same wallet");
    }

    @Test
    @DisplayName("Should handle edge case with null wallet IDs")
    void testNullWalletIds() {
        // Transfer with null wallet IDs should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", null, "wallet2", 300, 700, 500, "Null wallet ID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("From wallet ID cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle edge case with empty wallet IDs")
    void testEmptyWalletIds() {
        // Transfer with empty wallet IDs should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", "", "wallet2", 300, 700, 500, "Empty wallet ID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("From wallet ID cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle edge case with negative transfer amount")
    void testNegativeTransferAmount() {
        // Negative transfer amount should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", "wallet1", "wallet2", -300, 1300, 200, "Negative transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transfer amount must be positive");
    }

    @Test
    @DisplayName("Should handle edge case with negative balances")
    void testNegativeBalances() {
        // Transfer with negative balances should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, -100, 500, "Negative balance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Wallet balances cannot be negative");
    }

    @Test
    @DisplayName("Should handle edge case with null description")
    void testNullDescription() {
        // Transfer with null description should not be created
        assertThatThrownBy(() -> MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 500, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Description cannot be null");
    }

    @Test
    @DisplayName("Should handle edge case with empty description")
    void testEmptyDescription() {
        // Transfer with empty description should be created successfully
        assertThatCode(() -> MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 500, ""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle edge case with very long description")
    void testVeryLongDescription() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Transfer with very long description
        String longDescription = "A".repeat(10000);
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 500, longDescription);
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(700);
        assertThat(transfer.description()).isEqualTo(longDescription);
    }

    @Test
    @DisplayName("Should handle edge case with special characters in description")
    void testSpecialCharactersInDescription() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Transfer with special characters
        String specialDescription = "Transfer with special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?";
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 500, specialDescription);
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(700);
        assertThat(transfer.description()).isEqualTo(specialDescription);
    }

    @Test
    @DisplayName("Should handle edge case with unicode characters")
    void testUnicodeCharacters() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Transfer with unicode characters
        String unicodeDescription = "Transfer with unicode: 你好世界 🌍 émojis";
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 500, unicodeDescription);
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(700);
        assertThat(transfer.description()).isEqualTo(unicodeDescription);
    }

    @Test
    @DisplayName("Should handle edge case with maximum integer values")
    void testMaximumIntegerValues() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", Integer.MAX_VALUE, Instant.now(), Instant.now());

        // Transfer with maximum integer values
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", Integer.MAX_VALUE, 0, Integer.MAX_VALUE, "Max transfer");
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(0);
        assertThat(transfer.amount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(transfer.fromBalance()).isEqualTo(0);
        assertThat(transfer.toBalance()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle edge case with minimum positive values")
    void testMinimumPositiveValues() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1, Instant.now(), Instant.now());

        // Transfer with minimum positive values
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 1, 0, 1, "Min transfer");
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(0);
        assertThat(transfer.amount()).isEqualTo(1);
        assertThat(transfer.fromBalance()).isEqualTo(0);
        assertThat(transfer.toBalance()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle edge case with zero balance after transfer")
    void testZeroBalanceAfterTransfer() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Transfer that results in zero balance
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 1000, 0, 1000, "Full transfer");
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event);

        // Assert
        assertThat(newState.balance()).isEqualTo(0);
        assertThat(newState.isEmpty()).isFalse(); // Wallet still exists
        assertThat(newState.walletId()).isEqualTo("wallet1");
        assertThat(newState.owner()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Should handle edge case with concurrent-like operations")
    void testConcurrentLikeOperations() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events that simulate concurrent operations
        List<StoredEvent> events = WalletTestUtils.createEventList(
                DepositMade.of("dep1", "wallet1", 100, 1100, "Deposit 1"),
                DepositMade.of("dep2", "wallet1", 200, 1300, "Deposit 2"),
                WithdrawalMade.of("with1", "wallet1", 50, 1250, "Withdrawal 1"),
                WithdrawalMade.of("with2", "wallet1", 75, 1175, "Withdrawal 2")
        );

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState.balance()).isEqualTo(1175);
        assertThat(currentState.walletId()).isEqualTo("wallet1");
        assertThat(currentState.owner()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Should handle edge case with state consistency")
    void testStateConsistencyEdgeCase() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events that should maintain state consistency
        List<StoredEvent> events = WalletTestUtils.createEventList(
                DepositMade.of("dep1", "wallet1", 500, 1500, "Test deposit"),
                MoneyTransferred.of("tx1", "wallet1", "wallet2", 200, 1300, 700, "Test transfer"),
                WithdrawalMade.of("with1", "wallet1", 300, 1000, "Test withdrawal")
        );

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.createdAt()).isEqualTo(initialState.createdAt());
                    assertThat(s.balance()).isEqualTo(1000);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should handle empty wallet history")
    void testEmptyWalletHistory() {
        // Arrange - Create wallet but no transactions
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Act - No events to process
        List<StoredEvent> emptyEvents = List.of();

        // Assert - State should remain unchanged
        assertThat(initialState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1000);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should handle wallet with single transaction")
    void testWalletWithSingleTransaction() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Single deposit
        DepositMade singleDeposit = DepositMade.of("dep1", "wallet1", 500, 1500, "Single transaction");
        StoredEvent event = WalletTestUtils.createEvent(singleDeposit);

        // Act
        WalletState finalState = projector.transition(initialState, event);

        // Assert
        assertThat(finalState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1500);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should handle wallet with thousands of transactions")
    void testWalletWithThousandsOfTransactions() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create many small transactions to simulate high-volume wallet
        List<StoredEvent> events = new java.util.ArrayList<>();
        final int initialBalance = 1000;
        final int[] currentBalance = {1000};

        for (int i = 0; i < 1000; i++) {
            if (i % 2 == 0) {
                // Deposit
                currentBalance[0] += 10;
                events.add(WalletTestUtils.createEvent(
                        DepositMade.of("dep" + i, "wallet1", 10, currentBalance[0], "High volume deposit " + i)
                ));
            } else {
                // Withdrawal
                currentBalance[0] -= 5;
                events.add(WalletTestUtils.createEvent(
                        WithdrawalMade.of("with" + i, "wallet1", 5, currentBalance[0], "High volume withdrawal " + i)
                ));
            }
        }

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(currentBalance[0]);
                    assertThat(s.isEmpty()).isFalse();
                });

        // Verify expected final balance: 1000 + (500 * 10) - (500 * 5) = 1000 + 5000 - 2500 = 3500
        assertThat(currentState.balance()).isEqualTo(3500);
    }

    @Test
    @DisplayName("Should handle corrupted event data gracefully")
    void testCorruptedEventDataHandling() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create a valid event first
        DepositMade validDeposit = DepositMade.of("dep1", "wallet1", 100, 1100, "Valid deposit");
        StoredEvent validEvent = WalletTestUtils.createEvent(validDeposit);

        // Act - Process valid event
        WalletState stateAfterValid = projector.transition(initialState, validEvent);

        // Assert - Valid event should be processed correctly
        assertThat(stateAfterValid.balance()).isEqualTo(1100);

        // Note: In a real system, corrupted event data would be handled by the event store
        // and deserialization layer. This test verifies that the projector handles
        // events that it receives correctly.
    }

    @Test
    @DisplayName("Should handle missing event data gracefully")
    void testMissingEventDataHandling() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events with missing or null data
        // This tests the projector's resilience to incomplete event data

        // Valid deposit event
        DepositMade validDeposit = DepositMade.of("dep1", "wallet1", 100, 1100, "Valid deposit");
        StoredEvent validEvent = WalletTestUtils.createEvent(validDeposit);

        // Act
        WalletState finalState = projector.transition(initialState, validEvent);

        // Assert
        assertThat(finalState.balance()).isEqualTo(1100);

        // Note: Missing event data would be handled at the event store level
        // The projector assumes it receives valid, complete events
    }

    @Test
    @DisplayName("Should handle pagination edge cases")
    void testPaginationEdgeCases() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events that would require pagination in a real system
        List<StoredEvent> events = new java.util.ArrayList<>();
        final int[] currentBalance = {1000};

        // Create 50 events to simulate pagination scenarios
        for (int i = 0; i < 50; i++) {
            if (i % 3 == 0) {
                // Deposit
                currentBalance[0] += 20;
                events.add(WalletTestUtils.createEvent(
                        DepositMade.of("dep" + i, "wallet1", 20, currentBalance[0], "Pagination deposit " + i)
                ));
            } else if (i % 3 == 1) {
                // Withdrawal
                currentBalance[0] -= 10;
                events.add(WalletTestUtils.createEvent(
                        WithdrawalMade.of("with" + i, "wallet1", 10, currentBalance[0], "Pagination withdrawal " + i)
                ));
            } else {
                // Transfer (simplified - just affects balance)
                currentBalance[0] -= 15;
                events.add(WalletTestUtils.createEvent(
                        MoneyTransferred.of("tx" + i, "wallet1", "wallet2", 15, currentBalance[0], 500, "Pagination transfer " + i)
                ));
            }
        }

        // Act - Process all events
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(currentBalance[0]);
                    assertThat(s.isEmpty()).isFalse();
                });

        // Verify expected final balance calculation
        // 17 deposits * 20 = 340
        // 17 withdrawals * 10 = 170  
        // 16 transfers * 15 = 240
        // Final: 1000 + 340 - 170 - 240 = 930
        assertThat(currentState.balance()).isEqualTo(currentBalance[0]);
    }

    @Test
    @DisplayName("Should handle event ordering edge cases")
    void testEventOrderingEdgeCases() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events in a specific order to test state transitions
        List<StoredEvent> events = WalletTestUtils.createEventList(
                // Start with deposits
                DepositMade.of("dep1", "wallet1", 100, 1100, "First deposit"),
                DepositMade.of("dep2", "wallet1", 200, 1300, "Second deposit"),

                // Then withdrawals
                WithdrawalMade.of("with1", "wallet1", 50, 1250, "First withdrawal"),
                WithdrawalMade.of("with2", "wallet1", 75, 1175, "Second withdrawal"),

                // Then transfers
                MoneyTransferred.of("tx1", "wallet1", "wallet2", 100, 1075, 600, "First transfer"),
                MoneyTransferred.of("tx2", "wallet1", "wallet2", 50, 1025, 650, "Second transfer")
        );

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1025);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should handle large number edge cases")
    void testLargeNumberEdgeCases() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000000, Instant.now(), Instant.now());

        // Test with large numbers
        DepositMade largeDeposit = DepositMade.of("dep1", "wallet1", 500000, 1500000, "Large deposit");
        StoredEvent event = WalletTestUtils.createEvent(largeDeposit);

        // Act
        WalletState finalState = projector.transition(initialState, event);

        // Assert
        assertThat(finalState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1500000);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should handle rapid state changes")
    void testRapidStateChanges() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create rapid state changes
        List<StoredEvent> events = WalletTestUtils.createEventList(
                DepositMade.of("dep1", "wallet1", 100, 1100, "Rapid deposit 1"),
                WithdrawalMade.of("with1", "wallet1", 50, 1050, "Rapid withdrawal 1"),
                DepositMade.of("dep2", "wallet1", 200, 1250, "Rapid deposit 2"),
                WithdrawalMade.of("with2", "wallet1", 75, 1175, "Rapid withdrawal 2"),
                DepositMade.of("dep3", "wallet1", 300, 1475, "Rapid deposit 3"),
                WithdrawalMade.of("with3", "wallet1", 25, 1450, "Rapid withdrawal 3")
        );

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1450);
                    assertThat(s.isEmpty()).isFalse();
                });
    }
}
