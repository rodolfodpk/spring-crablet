package wallets.unit.features.query;
import wallets.integration.AbstractWalletIntegrationTest;

import com.crablet.eventstore.EventDeserializer;
import com.crablet.eventstore.StoredEvent;
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
import com.com.com.com.com.wallets.testutils.WalletTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test state projection logic with parameterized tests.
 * Uses AssertJ for assertions and JUnit 6 parameterized tests for comprehensive coverage.
 */
class WalletStateTransitionTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private static final EventDeserializer CONTEXT = WalletTestUtils.createEventDeserializer();
    private WalletStateProjector projector;

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @BeforeEach
    void setUp() {
        projector = new WalletStateProjector();
    }

    @Test
    @DisplayName("Should project WalletOpened event correctly")
    void testWalletStateProjectionRules() {
        // Arrange
        WalletOpened opened = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent event = WalletTestUtils.createEvent(opened);

        // Act
        WalletState state = projector.transition(WalletState.empty(), event, CONTEXT);

        // Assert
        assertThat(state)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1000);
                    assertThat(s.createdAt()).isEqualTo(opened.openedAt());
                    assertThat(s.updatedAt()).isEqualTo(opened.openedAt());
                });
    }

    @Test
    @DisplayName("Should project MoneyTransferred event as sender correctly")
    void testMoneyTransferStateTransitionRules() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // MoneyTransferred event (as sender)
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 500, "Test");
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event, CONTEXT);

        // Assert
        assertThat(newState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(700);
                    assertThat(s.updatedAt()).isEqualTo(transfer.transferredAt());
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.createdAt()).isEqualTo(initialState.createdAt());
                });
    }

    @Test
    @DisplayName("Should project MoneyTransferred event as receiver correctly")
    void testMoneyTransferStateTransitionRules_AsReceiver() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // MoneyTransferred event (as receiver)
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet2", "wallet1", 300, 700, 1300, "Test");
        StoredEvent event = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newState = projector.transition(initialState, event, CONTEXT);

        // Assert
        assertThat(newState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(1300);
                    assertThat(s.updatedAt()).isEqualTo(transfer.transferredAt());
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.createdAt()).isEqualTo(initialState.createdAt());
                });
    }


    @Test
    @DisplayName("Should project DepositMade event correctly")
    void testDepositStateTransitionRules() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // DepositMade event
        DepositMade deposit = DepositMade.of("dep1", "wallet1", 500, 1500, "Salary");
        StoredEvent event = WalletTestUtils.createEvent(deposit);

        // Act
        WalletState newState = projector.transition(initialState, event, CONTEXT);

        // Assert
        assertThat(newState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(1500);
                    assertThat(s.updatedAt()).isEqualTo(deposit.depositedAt());
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.createdAt()).isEqualTo(initialState.createdAt());
                });
    }

    @Test
    @DisplayName("Should project WithdrawalMade event correctly")
    void testWithdrawalStateTransitionRules() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // WithdrawalMade event
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", 300, 700, "Shopping");
        StoredEvent event = WalletTestUtils.createEvent(withdrawal);

        // Act
        WalletState newState = projector.transition(initialState, event, CONTEXT);

        // Assert
        assertThat(newState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(700);
                    assertThat(s.updatedAt()).isEqualTo(withdrawal.withdrawnAt());
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.createdAt()).isEqualTo(initialState.createdAt());
                });
    }

    @Test
    @DisplayName("Should handle unrelated events")
    void testUnrelatedEvents() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Event for different wallet
        WalletOpened otherWallet = WalletOpened.of("wallet2", "Bob", 500);
        StoredEvent event = WalletTestUtils.createEvent(otherWallet);

        // Act
        WalletState newState = projector.transition(initialState, event, CONTEXT);

        // Assert
        assertThat(newState).isEqualTo(initialState);
    }

    @Test
    @DisplayName("Should handle empty initial state")
    void testEmptyInitialState() {
        // Arrange
        WalletState emptyState = WalletState.empty();
        WalletOpened opened = WalletOpened.of("wallet1", "Alice", 1000);
        StoredEvent event = WalletTestUtils.createEvent(opened);

        // Act
        WalletState newState = projector.transition(emptyState, event, CONTEXT);

        // Assert
        assertThat(newState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1000);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, operation, amount, expectedBalance
            "1000, 'DEPOSIT', 500, 1500",
            "1000, 'WITHDRAWAL', 300, 700",
            "1000, 'TRANSFER_OUT', 200, 800",
            "1000, 'TRANSFER_IN', 150, 1150",
            "0, 'DEPOSIT', 1000, 1000",
            "1000, 'WITHDRAWAL', 1000, 0"
    })
    @DisplayName("Should update wallet state correctly for various operations")
    void testWalletStateUpdates(int initialBalance, String operation, int amount, int expectedBalance) {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", initialBalance, Instant.now(), Instant.now());
        StoredEvent event;

        // Create appropriate event based on operation
        switch (operation) {
            case "DEPOSIT" -> {
                DepositMade deposit = DepositMade.of("dep1", "wallet1", amount, expectedBalance, "Test deposit");
                event = WalletTestUtils.createEvent(deposit);
            }
            case "WITHDRAWAL" -> {
                WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", amount, expectedBalance, "Test withdrawal");
                event = WalletTestUtils.createEvent(withdrawal);
            }
            case "TRANSFER_OUT" -> {
                MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", amount, expectedBalance, 500, "Test transfer");
                event = WalletTestUtils.createEvent(transfer);
            }
            case "TRANSFER_IN" -> {
                MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet2", "wallet1", amount, 500, expectedBalance, "Test transfer");
                event = WalletTestUtils.createEvent(transfer);
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }

        // Act
        WalletState newState = projector.transition(initialState, event, CONTEXT);

        // Assert
        assertThat(newState.balance()).isEqualTo(expectedBalance);
        assertThat(newState.walletId()).isEqualTo(initialState.walletId());
        assertThat(newState.owner()).isEqualTo(initialState.owner());
        assertThat(newState.createdAt()).isEqualTo(initialState.createdAt());
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, depositAmount, withdrawalAmount, expectedBalance
            "1000, 500, 200, 1300",
            "500, 1000, 300, 1200",
            "2000, 100, 500, 1600",
            "100, 400, 50, 450"
    })
    @DisplayName("Should handle multiple operations correctly")
    void testMultipleOperations(int initialBalance, int depositAmount, int withdrawalAmount, int expectedBalance) {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", initialBalance, Instant.now(), Instant.now());

        // Create events
        DepositMade deposit = DepositMade.of("dep1", "wallet1", depositAmount, initialBalance + depositAmount, "Test deposit");
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", withdrawalAmount, expectedBalance, "Test withdrawal");

        List<StoredEvent> events = WalletTestUtils.createEventList(deposit, withdrawal);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event, CONTEXT);
        }

        // Assert
        assertThat(currentState.balance()).isEqualTo(expectedBalance);
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, transferOutAmount, transferInAmount, expectedBalance
            "1000, 200, 150, 950",
            "500, 100, 300, 700",
            "2000, 500, 500, 2000",
            "100, 50, 200, 250"
    })
    @DisplayName("Should handle transfer operations correctly")
    void testTransferOperations(int initialBalance, int transferOutAmount, int transferInAmount, int expectedBalance) {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", initialBalance, Instant.now(), Instant.now());

        // Create transfer events
        MoneyTransferred transferOut = MoneyTransferred.of("tx1", "wallet1", "wallet2", transferOutAmount,
                initialBalance - transferOutAmount, 500, "Test transfer out");
        MoneyTransferred transferIn = MoneyTransferred.of("tx2", "wallet3", "wallet1", transferInAmount,
                500, expectedBalance, "Test transfer in");

        List<StoredEvent> events = WalletTestUtils.createEventList(transferOut, transferIn);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event, CONTEXT);
        }

        // Assert
        assertThat(currentState.balance()).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("Should handle complex event sequence")
    void testComplexEventSequence() {
        // Arrange
        WalletState initialState = WalletState.empty();

        // Create complex event sequence
        WalletOpened opened = WalletOpened.of("wallet1", "Alice", 1000);
        DepositMade deposit1 = DepositMade.of("dep1", "wallet1", 500, 1500, "Salary");
        MoneyTransferred transfer1 = MoneyTransferred.of("tx1", "wallet1", "wallet2", 200, 1300, 700, "Transfer 1");
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", 300, 1000, "Shopping");
        MoneyTransferred transfer2 = MoneyTransferred.of("tx2", "wallet3", "wallet1", 100, 500, 1100, "Transfer 2");
        DepositMade deposit2 = DepositMade.of("dep2", "wallet1", 200, 1300, "Bonus");

        List<StoredEvent> events = WalletTestUtils.createEventList(opened, deposit1, transfer1, withdrawal, transfer2, deposit2);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event, CONTEXT);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.balance()).isEqualTo(1300);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should maintain state consistency across transitions")
    void testStateConsistency() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events that should maintain consistency
        DepositMade deposit = DepositMade.of("dep1", "wallet1", 500, 1500, "Test deposit");
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", 300, 1200, "Test withdrawal");

        List<StoredEvent> events = WalletTestUtils.createEventList(deposit, withdrawal);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event, CONTEXT);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.createdAt()).isEqualTo(initialState.createdAt());
                    assertThat(s.balance()).isEqualTo(1200);
                });
    }
}
