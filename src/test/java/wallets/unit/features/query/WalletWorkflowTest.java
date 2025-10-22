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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import wallets.testutils.WalletTestUtils;
import wallets.testutils.WorkflowScenario;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test complete business workflows with parameterized tests.
 * Uses AssertJ for assertions and JUnit 6 parameterized tests for comprehensive coverage.
 */
class WalletWorkflowTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private WalletStateProjector projector;

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    static Stream<Arguments> workflowScenarios() {
        return Stream.of(
                Arguments.of(new WorkflowScenario("Simple transfer", 1000, 500, 300)),
                Arguments.of(new WorkflowScenario("Maximum transfer", 1000, 500, 1000)),
                Arguments.of(new WorkflowScenario("Small transfer", 100, 50, 10)),
                Arguments.of(new WorkflowScenario("Equal balance transfer", 500, 500, 250)),
                Arguments.of(new WorkflowScenario("Large transfer", 5000, 3000, 2000)),
                Arguments.of(new WorkflowScenario("Minimum transfer", 1000, 500, 1))
        );
    }

    @BeforeEach
    void setUp() {
        projector = new WalletStateProjector("wallet1", OBJECT_MAPPER);
    }

    @Test
    @DisplayName("Should handle complete wallet lifecycle workflow")
    void testWalletLifecycleWorkflow() {
        // Arrange
        WalletState initialState = WalletState.empty();

        // Create complete lifecycle events
        WalletOpened opened = WalletOpened.of("wallet1", "Alice", 1000);
        DepositMade deposit1 = DepositMade.of("dep1", "wallet1", 500, 1500, "Salary");
        WithdrawalMade withdrawal1 = WithdrawalMade.of("with1", "wallet1", 200, 1300, "Shopping");
        MoneyTransferred transfer1 = MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 1000, 700, "Transfer 1");
        DepositMade deposit2 = DepositMade.of("dep2", "wallet1", 1000, 2000, "Bonus");
        WithdrawalMade withdrawal2 = WithdrawalMade.of("with2", "wallet1", 500, 1500, "Vacation");
        List<StoredEvent> events = WalletTestUtils.createEventList(opened, deposit1, withdrawal1, transfer1, deposit2, withdrawal2);

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
                    assertThat(s.balance()).isEqualTo(1500);
                    assertThat(s.isEmpty()).isFalse();
                });
    }

    @Test
    @DisplayName("Should handle transfer workflow with multiple wallets")
    void testTransferWorkflowBusinessRules() {
        // Arrange
        WalletStateProjector fromProjector = new WalletStateProjector("wallet1", OBJECT_MAPPER);
        WalletStateProjector toProjector = new WalletStateProjector("wallet2", OBJECT_MAPPER);

        // Initial states
        WalletState fromState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());
        WalletState toState = new WalletState("wallet2", "Bob", 500, Instant.now(), Instant.now());

        // Transfer event
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 300, 700, 800, "Test transfer");
        StoredEvent transferEvent = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newFromState = fromProjector.transition(fromState, transferEvent);
        WalletState newToState = toProjector.transition(toState, transferEvent);

        // Assert
        assertThat(newFromState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(700);
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                });

        assertThat(newToState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(800);
                    assertThat(s.walletId()).isEqualTo("wallet2");
                    assertThat(s.owner()).isEqualTo("Bob");
                });

        // Verify money conservation
        int totalBefore = fromState.balance() + toState.balance();
        int totalAfter = newFromState.balance() + newToState.balance();
        assertThat(totalAfter).isEqualTo(totalBefore).as("Total money should be conserved");
    }

    @Test
    @DisplayName("Should handle complex transfer scenario with multiple transfers")
    void testComplexTransferScenario() {
        // Arrange
        WalletStateProjector wallet1Projector = new WalletStateProjector("wallet1", OBJECT_MAPPER);
        WalletStateProjector wallet2Projector = new WalletStateProjector("wallet2", OBJECT_MAPPER);
        WalletStateProjector wallet3Projector = new WalletStateProjector("wallet3", OBJECT_MAPPER);

        // Initial states
        WalletState wallet1State = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());
        WalletState wallet2State = new WalletState("wallet2", "Bob", 500, Instant.now(), Instant.now());
        WalletState wallet3State = new WalletState("wallet3", "Charlie", 300, Instant.now(), Instant.now());

        // Complex transfer scenario
        MoneyTransferred transfer1 = MoneyTransferred.of("tx1", "wallet1", "wallet2", 200, 800, 700, "Transfer 1");
        MoneyTransferred transfer2 = MoneyTransferred.of("tx2", "wallet2", "wallet3", 100, 600, 400, "Transfer 2");
        MoneyTransferred transfer3 = MoneyTransferred.of("tx3", "wallet3", "wallet1", 50, 350, 850, "Transfer 3");

        List<StoredEvent> events = WalletTestUtils.createEventList(transfer1, transfer2, transfer3);

        // Act
        WalletState currentWallet1State = wallet1State;
        WalletState currentWallet2State = wallet2State;
        WalletState currentWallet3State = wallet3State;

        for (StoredEvent event : events) {
            currentWallet1State = wallet1Projector.transition(currentWallet1State, event);
            currentWallet2State = wallet2Projector.transition(currentWallet2State, event);
            currentWallet3State = wallet3Projector.transition(currentWallet3State, event);
        }

        // Assert
        assertThat(currentWallet1State.balance()).isEqualTo(850);
        assertThat(currentWallet2State.balance()).isEqualTo(600);
        assertThat(currentWallet3State.balance()).isEqualTo(350);

        // Verify money conservation
        int totalBefore = wallet1State.balance() + wallet2State.balance() + wallet3State.balance();
        int totalAfter = currentWallet1State.balance() + currentWallet2State.balance() + currentWallet3State.balance();
        assertThat(totalAfter).isEqualTo(totalBefore).as("Total money should be conserved across all transfers");
    }

    @ParameterizedTest
    @MethodSource("workflowScenarios")
    @DisplayName("Should handle complete workflow scenarios")
    void testWorkflowScenarios(WorkflowScenario scenario) {
        // Arrange
        WalletStateProjector fromProjector = new WalletStateProjector("wallet1", OBJECT_MAPPER);
        WalletStateProjector toProjector = new WalletStateProjector("wallet2", OBJECT_MAPPER);

        // Initial states
        WalletState fromState = new WalletState("wallet1", "Alice", scenario.fromBalance(), Instant.now(), Instant.now());
        WalletState toState = new WalletState("wallet2", "Bob", scenario.toBalance(), Instant.now(), Instant.now());

        // Transfer event
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", scenario.transferAmount(),
                scenario.expectedFromBalance(), scenario.expectedToBalance(), "Test transfer");
        StoredEvent transferEvent = WalletTestUtils.createEvent(transfer);

        // Act
        WalletState newFromState = fromProjector.transition(fromState, transferEvent);
        WalletState newToState = toProjector.transition(toState, transferEvent);

        // Assert
        assertThat(scenario).isNotNull();
        assertThat(scenario.name()).isNotBlank();
        assertThat(scenario.fromBalance()).isGreaterThanOrEqualTo(0);
        assertThat(scenario.toBalance()).isGreaterThanOrEqualTo(0);
        assertThat(scenario.transferAmount()).isGreaterThan(0);

        if (scenario.isValidTransfer()) {
            assertThat(newFromState.balance()).isEqualTo(scenario.expectedFromBalance());
            assertThat(newToState.balance()).isEqualTo(scenario.expectedToBalance());
            assertThat(scenario.isMoneyConserved()).isTrue();
        } else {
            // For invalid transfers, we expect the original balances to be maintained
            assertThat(newFromState.balance()).isEqualTo(scenario.fromBalance());
            assertThat(newToState.balance()).isEqualTo(scenario.toBalance());
        }
    }

    @Test
    @DisplayName("Should handle deposit and withdrawal workflow")
    void testDepositWithdrawalWorkflow() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create deposit and withdrawal events
        DepositMade deposit1 = DepositMade.of("dep1", "wallet1", 500, 1500, "Salary");
        WithdrawalMade withdrawal1 = WithdrawalMade.of("with1", "wallet1", 200, 1300, "Shopping");
        DepositMade deposit2 = DepositMade.of("dep2", "wallet1", 1000, 2300, "Bonus");
        WithdrawalMade withdrawal2 = WithdrawalMade.of("with2", "wallet1", 300, 2000, "Vacation");

        List<StoredEvent> events = WalletTestUtils.createEventList(deposit1, withdrawal1, deposit2, withdrawal2);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(2000);
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                });
    }

    @Test
    @DisplayName("Should handle mixed operations workflow")
    void testMixedOperationsWorkflow() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create mixed operations
        DepositMade deposit = DepositMade.of("dep1", "wallet1", 500, 1500, "Salary");
        MoneyTransferred transferOut = MoneyTransferred.of("tx1", "wallet1", "wallet2", 200, 1300, 700, "Transfer out");
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", 300, 1000, "Shopping");
        MoneyTransferred transferIn = MoneyTransferred.of("tx2", "wallet3", "wallet1", 100, 500, 1100, "Transfer in");
        DepositMade finalDeposit = DepositMade.of("dep2", "wallet1", 200, 1300, "Bonus");

        List<StoredEvent> events = WalletTestUtils.createEventList(deposit, transferOut, withdrawal, transferIn, finalDeposit);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(1300);
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                });
    }

    @Test
    @DisplayName("Should handle high-frequency operations workflow")
    void testHighFrequencyOperationsWorkflow() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create high-frequency operations
        List<StoredEvent> events = WalletTestUtils.createEventList(
                DepositMade.of("dep1", "wallet1", 100, 1100, "Deposit 1"),
                WithdrawalMade.of("with1", "wallet1", 50, 1050, "Withdrawal 1"),
                DepositMade.of("dep2", "wallet1", 200, 1250, "Deposit 2"),
                WithdrawalMade.of("with2", "wallet1", 75, 1175, "Withdrawal 2"),
                DepositMade.of("dep3", "wallet1", 300, 1475, "Deposit 3"),
                WithdrawalMade.of("with3", "wallet1", 125, 1350, "Withdrawal 3"),
                DepositMade.of("dep4", "wallet1", 150, 1500, "Deposit 4"),
                WithdrawalMade.of("with4", "wallet1", 100, 1400, "Withdrawal 4")
        );

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(1400);
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                });
    }

    @Test
    @DisplayName("Should handle edge case workflow with zero balances")
    void testEdgeCaseWorkflowWithZeroBalances() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 0, Instant.now(), Instant.now());

        // Create events that result in zero balance
        DepositMade deposit = DepositMade.of("dep1", "wallet1", 1000, 1000, "Initial deposit");
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", 1000, 0, "Full withdrawal");

        List<StoredEvent> events = WalletTestUtils.createEventList(deposit, withdrawal);

        // Act
        WalletState currentState = initialState;
        for (StoredEvent event : events) {
            currentState = projector.transition(currentState, event);
        }

        // Assert
        assertThat(currentState)
                .satisfies(s -> {
                    assertThat(s.balance()).isEqualTo(0);
                    assertThat(s.walletId()).isEqualTo("wallet1");
                    assertThat(s.owner()).isEqualTo("Alice");
                    assertThat(s.isEmpty()).isFalse(); // Wallet exists but has zero balance
                });
    }

    @Test
    @DisplayName("Should handle workflow with state consistency checks")
    void testWorkflowWithStateConsistency() {
        // Arrange
        WalletState initialState = new WalletState("wallet1", "Alice", 1000, Instant.now(), Instant.now());

        // Create events that should maintain state consistency
        DepositMade deposit = DepositMade.of("dep1", "wallet1", 500, 1500, "Test deposit");
        MoneyTransferred transfer = MoneyTransferred.of("tx1", "wallet1", "wallet2", 200, 1300, 700, "Test transfer");
        WithdrawalMade withdrawal = WithdrawalMade.of("with1", "wallet1", 300, 1000, "Test withdrawal");

        List<StoredEvent> events = WalletTestUtils.createEventList(deposit, transfer, withdrawal);

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
}
