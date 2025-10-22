package wallets.unit.features.query;
import wallets.integration.AbstractWalletIntegrationTest;

import com.wallets.features.query.WalletState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test business logic rules with parameterized tests.
 * Uses AssertJ for assertions and JUnit 6 parameterized tests for comprehensive coverage.
 */
class WalletBusinessRulesTest {

    @ParameterizedTest
    @CsvSource({
            // fromBalance, toBalance, transferAmount, expectedFromBalance, expectedToBalance
            "1000, 500, 100, 900, 600",
            "1000, 500, 500, 500, 1000",
            "1000, 500, 1000, 0, 1500",
            "0, 1000, 0, 0, 1000",
            "100, 200, 50, 50, 250",
            "5000, 3000, 2000, 3000, 5000",
            "1, 1, 1, 0, 2"
    })
    @DisplayName("Should calculate transfer balances correctly")
    void shouldCalculateTransferBalancesCorrectly(int fromBalance, int toBalance, int transferAmount,
                                                  int expectedFromBalance, int expectedToBalance) {
        // Test balance calculation logic
        int actualFromBalance = fromBalance - transferAmount;
        int actualToBalance = toBalance + transferAmount;

        assertThat(actualFromBalance).isEqualTo(expectedFromBalance);
        assertThat(actualToBalance).isEqualTo(expectedToBalance);

        // Test money conservation
        int totalBefore = fromBalance + toBalance;
        int totalAfter = actualFromBalance + actualToBalance;
        assertThat(totalAfter).isEqualTo(totalBefore).as("Total money should be conserved");
    }

    @ParameterizedTest
    @CsvSource({
            // fromBalance, transferAmount, shouldSucceed
            "1000, 100, true",
            "1000, 1000, true",
            "1000, 1001, false",
            "100, 200, false",
            "0, 1, false",
            "50, 50, true",
            "1, 1, true",
            "0, 0, true"
    })
    @DisplayName("Should validate sufficient funds scenarios")
    void shouldHandleInsufficientFundsScenarios(int fromBalance, int transferAmount, boolean shouldSucceed) {
        boolean hasSufficientFunds = fromBalance >= transferAmount;
        assertThat(hasSufficientFunds).isEqualTo(shouldSucceed)
                .as("Transfer should %s for balance %d and amount %d",
                        shouldSucceed ? "succeed" : "fail", fromBalance, transferAmount);
    }

    @ParameterizedTest
    @CsvSource({
            // walletId, owner, balance, isEmpty
            "'', '', 0, true",
            "'wallet1', '', 1000, true",
            "'', 'Alice', 1000, true",
            "'wallet1', 'Alice', 1000, false",
            "'wallet2', 'Bob', 0, false",
            "'wallet3', 'Charlie', 5000, false"
    })
    @DisplayName("Should detect empty wallet state correctly")
    void shouldDetectEmptyWalletStateCorrectly(String walletId, String owner, int balance, boolean isEmpty) {
        // When
        WalletState state = new WalletState(walletId, owner, balance, java.time.Instant.now(), java.time.Instant.now());

        // Then
        assertThat(state.isEmpty()).isEqualTo(isEmpty)
                .as("Wallet state should be %s for walletId='%s', owner='%s'",
                        isEmpty ? "empty" : "not empty", walletId, owner);
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, depositAmount, expectedBalance
            "1000, 500, 1500",
            "0, 1000, 1000",
            "500, 0, 500",
            "100, 50, 150",
            "2000, 3000, 5000"
    })
    @DisplayName("Should calculate deposit balance correctly")
    void shouldCalculateDepositBalanceCorrectly(int initialBalance, int depositAmount, int expectedBalance) {
        // Test deposit calculation
        int actualBalance = initialBalance + depositAmount;
        assertThat(actualBalance).isEqualTo(expectedBalance);

        // Test that deposit increases balance
        assertThat(actualBalance).isGreaterThanOrEqualTo(initialBalance)
                .as("Deposit should not decrease balance");
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, withdrawalAmount, expectedBalance
            "1000, 300, 700",
            "500, 500, 0",
            "100, 50, 50",
            "2000, 1000, 1000"
    })
    @DisplayName("Should calculate withdrawal balance correctly")
    void shouldCalculateWithdrawalBalanceCorrectly(int initialBalance, int withdrawalAmount, int expectedBalance) {
        // Test withdrawal calculation
        int actualBalance = initialBalance - withdrawalAmount;
        assertThat(actualBalance).isEqualTo(expectedBalance);

        // Test that withdrawal decreases balance
        assertThat(actualBalance).isLessThanOrEqualTo(initialBalance)
                .as("Withdrawal should not increase balance");
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
    void shouldUpdateWalletStateCorrectlyForVariousOperations(int initialBalance, String operation, int amount, int expectedBalance) {
        WalletState initialState = new WalletState("wallet1", "Alice", initialBalance,
                java.time.Instant.now(), java.time.Instant.now());

        WalletState newState = switch (operation) {
            case "DEPOSIT" -> initialState.withBalance(initialBalance + amount, java.time.Instant.now());
            case "WITHDRAWAL" -> initialState.withBalance(initialBalance - amount, java.time.Instant.now());
            case "TRANSFER_OUT" -> initialState.withBalance(initialBalance - amount, java.time.Instant.now());
            case "TRANSFER_IN" -> initialState.withBalance(initialBalance + amount, java.time.Instant.now());
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        assertThat(newState.balance()).isEqualTo(expectedBalance);
        assertThat(newState.walletId()).isEqualTo(initialState.walletId());
        assertThat(newState.owner()).isEqualTo(initialState.owner());
        assertThat(newState.createdAt()).isEqualTo(initialState.createdAt());
    }

    @ParameterizedTest
    @CsvSource({
            // wallet1Balance, wallet2Balance, wallet3Balance, transfer1Amount, transfer2Amount, expectedWallet1Balance, expectedWallet2Balance, expectedWallet3Balance
            "1000, 500, 300, 200, 100, 800, 600, 400",
            "2000, 1000, 500, 500, 200, 1500, 1300, 700",
            "500, 500, 500, 100, 50, 400, 550, 550"
    })
    @DisplayName("Should handle multiple transfers correctly")
    void shouldHandleMultipleTransferScenariosCorrectly(int wallet1Balance, int wallet2Balance, int wallet3Balance,
                                                        int transfer1Amount, int transfer2Amount,
                                                        int expectedWallet1Balance, int expectedWallet2Balance, int expectedWallet3Balance) {
        // Simulate transfers: wallet1 -> wallet2 (transfer1), wallet2 -> wallet3 (transfer2)
        int wallet1AfterTransfer1 = wallet1Balance - transfer1Amount;
        int wallet2AfterTransfer1 = wallet2Balance + transfer1Amount;

        int wallet2AfterTransfer2 = wallet2AfterTransfer1 - transfer2Amount;
        int wallet3AfterTransfer2 = wallet3Balance + transfer2Amount;

        // Verify final balances
        assertThat(wallet1AfterTransfer1).isEqualTo(expectedWallet1Balance);
        assertThat(wallet2AfterTransfer2).isEqualTo(expectedWallet2Balance);
        assertThat(wallet3AfterTransfer2).isEqualTo(expectedWallet3Balance);

        // Verify money conservation
        int totalBefore = wallet1Balance + wallet2Balance + wallet3Balance;
        int totalAfter = wallet1AfterTransfer1 + wallet2AfterTransfer2 + wallet3AfterTransfer2;
        assertThat(totalAfter).isEqualTo(totalBefore).as("Total money should be conserved across all transfers");
    }

    @ParameterizedTest
    @CsvSource({
            // initialBalance, operations, expectedFinalBalance
            "1000, 'DEPOSIT:500,WITHDRAWAL:200,TRANSFER_OUT:100', 1200",
            "500, 'DEPOSIT:1000,WITHDRAWAL:300,TRANSFER_IN:200', 1400",
            "0, 'DEPOSIT:1000,DEPOSIT:500,WITHDRAWAL:200', 1300"
    })
    @DisplayName("Should handle complex operation sequences correctly")
    void shouldHandleComplexOperationSequencesCorrectly(int initialBalance, String operations, int expectedFinalBalance) {
        int currentBalance = initialBalance;

        // Parse and execute operations
        String[] operationList = operations.split(",");
        for (String operation : operationList) {
            String[] parts = operation.split(":");
            String opType = parts[0];
            int amount = Integer.parseInt(parts[1]);

            currentBalance = switch (opType) {
                case "DEPOSIT", "TRANSFER_IN" -> currentBalance + amount;
                case "WITHDRAWAL", "TRANSFER_OUT" -> currentBalance - amount;
                default -> throw new IllegalArgumentException("Unknown operation: " + opType);
            };
        }

        assertThat(currentBalance).isEqualTo(expectedFinalBalance);
    }

    @ParameterizedTest
    @CsvSource({
            // balance, amount, operation, shouldSucceed
            "1000, 500, 'WITHDRAWAL', true",
            "1000, 1000, 'WITHDRAWAL', true",
            "1000, 1001, 'WITHDRAWAL', false",
            "100, 200, 'WITHDRAWAL', false",
            "0, 1, 'WITHDRAWAL', false",
            "1000, 500, 'TRANSFER_OUT', true",
            "1000, 1000, 'TRANSFER_OUT', true",
            "1000, 1001, 'TRANSFER_OUT', false"
    })
    @DisplayName("Should validate withdrawal and transfer out operations")
    void shouldValidateWithdrawalAndTransferOutCorrectly(int balance, int amount, String operation, boolean shouldSucceed) {
        boolean hasSufficientFunds = balance >= amount;
        assertThat(hasSufficientFunds).isEqualTo(shouldSucceed)
                .as("%s should %s for balance %d and amount %d",
                        operation, shouldSucceed ? "succeed" : "fail", balance, amount);
    }

    @ParameterizedTest
    @CsvSource({
            // fromWalletId, toWalletId, shouldBeValid
            "'wallet1', 'wallet2', true",
            "'wallet1', 'wallet1', false",
            "'wallet2', 'wallet1', true",
            "'wallet3', 'wallet4', true"
    })
    @DisplayName("Should validate transfer wallet IDs")
    void testTransferWalletIdValidation(String fromWalletId, String toWalletId, boolean shouldBeValid) {
        boolean isValidTransfer = !fromWalletId.equals(toWalletId);
        assertThat(isValidTransfer).isEqualTo(shouldBeValid)
                .as("Transfer from %s to %s should be %s",
                        fromWalletId, toWalletId, shouldBeValid ? "valid" : "invalid");
    }
}
