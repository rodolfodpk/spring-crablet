package unit.features.query;

import com.wallets.domain.event.DepositMade;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.features.query.WalletState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation tests for wallet domain objects.
 * Tests business rules, constraints, and validation logic for domain entities.
 */
class WalletDomainValidationTest {

    @Test
    @DisplayName("Should validate WalletOpened event creation")
    void shouldValidateWalletOpenedEventCreation() {
        // Given
        String walletId = "test-wallet-123";
        String owner = "John Doe";
        int initialBalance = 1000;

        // When
        WalletOpened walletOpened = WalletOpened.of(walletId, owner, initialBalance);

        // Then
        assertThat(walletOpened.walletId()).isEqualTo(walletId);
        assertThat(walletOpened.owner()).isEqualTo(owner);
        assertThat(walletOpened.initialBalance()).isEqualTo(initialBalance);
        assertThat(walletOpened.openedAt()).isNotNull();
        assertThat(walletOpened.getEventType()).isEqualTo("WalletOpened");
        assertThat(walletOpened.getOccurredAt()).isEqualTo(walletOpened.openedAt());
    }

    @Test
    @DisplayName("Should reject WalletOpened with null wallet ID")
    void shouldRejectWalletOpenedWithNullWalletId() {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of(null, "John Doe", 1000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WalletOpened with empty wallet ID")
    void shouldRejectWalletOpenedWithEmptyWalletId() {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of("", "John Doe", 1000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WalletOpened with null owner")
    void shouldRejectWalletOpenedWithNullOwner() {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of("wallet-123", null, 1000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WalletOpened with empty owner")
    void shouldRejectWalletOpenedWithEmptyOwner() {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of("wallet-123", "", 1000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WalletOpened with negative initial balance")
    void shouldRejectWalletOpenedWithNegativeInitialBalance() {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of("wallet-123", "John Doe", -100))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should accept WalletOpened with zero initial balance")
    void shouldAcceptWalletOpenedWithZeroInitialBalance() {
        // When
        WalletOpened walletOpened = WalletOpened.of("wallet-123", "John Doe", 0);

        // Then
        assertThat(walletOpened.initialBalance()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should validate MoneyTransferred event creation")
    void shouldValidateMoneyTransferredEventCreation() {
        // Given
        String transferId = "transfer-123";
        String fromWalletId = "wallet-1";
        String toWalletId = "wallet-2";
        int amount = 500;
        int fromBalance = 1000;
        int toBalance = 1500;
        String description = "Test transfer";
        Instant transferredAt = Instant.now();

        // When
        MoneyTransferred transfer = MoneyTransferred.of(
            transferId, fromWalletId, toWalletId, amount, 
            fromBalance, toBalance, description
        );

        // Then
        assertThat(transfer.transferId()).isEqualTo(transferId);
        assertThat(transfer.fromWalletId()).isEqualTo(fromWalletId);
        assertThat(transfer.toWalletId()).isEqualTo(toWalletId);
        assertThat(transfer.amount()).isEqualTo(amount);
        assertThat(transfer.fromBalance()).isEqualTo(fromBalance);
        assertThat(transfer.toBalance()).isEqualTo(toBalance);
        assertThat(transfer.description()).isEqualTo(description);
        assertThat(transfer.transferredAt()).isNotNull();
        assertThat(transfer.getEventType()).isEqualTo("MoneyTransferred");
        assertThat(transfer.getOccurredAt()).isEqualTo(transfer.transferredAt());
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with null transfer ID")
    void shouldRejectMoneyTransferredWithNullTransferId() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            null, "wallet-1", "wallet-2", 500, 1000, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with empty transfer ID")
    void shouldRejectMoneyTransferredWithEmptyTransferId() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "", "wallet-1", "wallet-2", 500, 1000, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with null from wallet ID")
    void shouldRejectMoneyTransferredWithNullFromWalletId() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", null, "wallet-2", 500, 1000, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with null to wallet ID")
    void shouldRejectMoneyTransferredWithNullToWalletId() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", null, 500, 1000, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with same from and to wallet")
    void shouldRejectMoneyTransferredWithSameFromAndToWallet() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-1", 500, 1000, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with zero amount")
    void shouldRejectMoneyTransferredWithZeroAmount() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", 0, 1000, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with negative amount")
    void shouldRejectMoneyTransferredWithNegativeAmount() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", -100, 1000, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with negative from balance")
    void shouldRejectMoneyTransferredWithNegativeFromBalance() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", 500, -100, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with negative to balance")
    void shouldRejectMoneyTransferredWithNegativeToBalance() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", 500, 1000, -100, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject MoneyTransferred with null description")
    void shouldRejectMoneyTransferredWithNullDescription() {
        // When & Then
        assertThatThrownBy(() -> MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", 500, 1000, 1500, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should accept MoneyTransferred with empty description")
    void shouldAcceptMoneyTransferredWithEmptyDescription() {
        // When
        MoneyTransferred transfer = MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", 500, 1000, 1500, ""
        );

        // Then
        assertThat(transfer.description()).isEmpty();
    }

    @Test
    @DisplayName("Should validate DepositMade event creation")
    void shouldValidateDepositMadeEventCreation() {
        // Given
        String depositId = "deposit-123";
        String walletId = "wallet-1";
        int amount = 500;
        int newBalance = 1500;
        String description = "Salary deposit";
        Instant depositedAt = Instant.now();

        // When
        DepositMade deposit = DepositMade.of(
            depositId, walletId, amount, newBalance, description
        );

        // Then
        assertThat(deposit.depositId()).isEqualTo(depositId);
        assertThat(deposit.walletId()).isEqualTo(walletId);
        assertThat(deposit.amount()).isEqualTo(amount);
        assertThat(deposit.newBalance()).isEqualTo(newBalance);
        assertThat(deposit.description()).isEqualTo(description);
        assertThat(deposit.depositedAt()).isNotNull();
        assertThat(deposit.getEventType()).isEqualTo("DepositMade");
        assertThat(deposit.getOccurredAt()).isEqualTo(deposit.depositedAt());
    }

    @Test
    @DisplayName("Should reject DepositMade with null deposit ID")
    void shouldRejectDepositMadeWithNullDepositId() {
        // When & Then
        assertThatThrownBy(() -> DepositMade.of(
            null, "wallet-1", 500, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject DepositMade with null wallet ID")
    void shouldRejectDepositMadeWithNullWalletId() {
        // When & Then
        assertThatThrownBy(() -> DepositMade.of(
            "deposit-123", null, 500, 1500, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject DepositMade with zero amount")
    void shouldRejectDepositMadeWithZeroAmount() {
        // When & Then
        assertThatThrownBy(() -> DepositMade.of(
            "deposit-123", "wallet-1", 0, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject DepositMade with negative amount")
    void shouldRejectDepositMadeWithNegativeAmount() {
        // When & Then
        assertThatThrownBy(() -> DepositMade.of(
            "deposit-123", "wallet-1", -100, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject DepositMade with negative new balance")
    void shouldRejectDepositMadeWithNegativeNewBalance() {
        // When & Then
        assertThatThrownBy(() -> DepositMade.of(
            "deposit-123", "wallet-1", 500, -100, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject DepositMade with null description")
    void shouldRejectDepositMadeWithNullDescription() {
        // When & Then
        assertThatThrownBy(() -> DepositMade.of(
            "deposit-123", "wallet-1", 500, 1500, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should validate WithdrawalMade event creation")
    void shouldValidateWithdrawalMadeEventCreation() {
        // Given
        String withdrawalId = "withdrawal-123";
        String walletId = "wallet-1";
        int amount = 300;
        int newBalance = 700;
        String description = "ATM withdrawal";
        Instant withdrawnAt = Instant.now();

        // When
        WithdrawalMade withdrawal = WithdrawalMade.of(
            withdrawalId, walletId, amount, newBalance, description
        );

        // Then
        assertThat(withdrawal.withdrawalId()).isEqualTo(withdrawalId);
        assertThat(withdrawal.walletId()).isEqualTo(walletId);
        assertThat(withdrawal.amount()).isEqualTo(amount);
        assertThat(withdrawal.newBalance()).isEqualTo(newBalance);
        assertThat(withdrawal.description()).isEqualTo(description);
        assertThat(withdrawal.withdrawnAt()).isNotNull();
        assertThat(withdrawal.getEventType()).isEqualTo("WithdrawalMade");
        assertThat(withdrawal.getOccurredAt()).isEqualTo(withdrawal.withdrawnAt());
    }

    @Test
    @DisplayName("Should reject WithdrawalMade with null withdrawal ID")
    void shouldRejectWithdrawalMadeWithNullWithdrawalId() {
        // When & Then
        assertThatThrownBy(() -> WithdrawalMade.of(
            null, "wallet-1", 300, 700, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WithdrawalMade with null wallet ID")
    void shouldRejectWithdrawalMadeWithNullWalletId() {
        // When & Then
        assertThatThrownBy(() -> WithdrawalMade.of(
            "withdrawal-123", null, 300, 700, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WithdrawalMade with zero amount")
    void shouldRejectWithdrawalMadeWithZeroAmount() {
        // When & Then
        assertThatThrownBy(() -> WithdrawalMade.of(
            "withdrawal-123", "wallet-1", 0, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WithdrawalMade with negative amount")
    void shouldRejectWithdrawalMadeWithNegativeAmount() {
        // When & Then
        assertThatThrownBy(() -> WithdrawalMade.of(
            "withdrawal-123", "wallet-1", -100, 1000, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WithdrawalMade with negative new balance")
    void shouldRejectWithdrawalMadeWithNegativeNewBalance() {
        // When & Then
        assertThatThrownBy(() -> WithdrawalMade.of(
            "withdrawal-123", "wallet-1", 300, -100, "Test"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject WithdrawalMade with null description")
    void shouldRejectWithdrawalMadeWithNullDescription() {
        // When & Then
        assertThatThrownBy(() -> WithdrawalMade.of(
            "withdrawal-123", "wallet-1", 300, 700, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should validate WalletState creation")
    void shouldValidateWalletStateCreation() {
        // Given
        String walletId = "wallet-123";
        String owner = "John Doe";
        int balance = 1000;
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();

        // When
        WalletState walletState = new WalletState(walletId, owner, balance, createdAt, updatedAt);

        // Then
        assertThat(walletState.walletId()).isEqualTo(walletId);
        assertThat(walletState.owner()).isEqualTo(owner);
        assertThat(walletState.balance()).isEqualTo(balance);
        assertThat(walletState.createdAt()).isEqualTo(createdAt);
        assertThat(walletState.updatedAt()).isEqualTo(updatedAt);
        assertThat(walletState.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should validate empty WalletState")
    void shouldValidateEmptyWalletState() {
        // When
        WalletState emptyState = WalletState.empty();

        // Then
        assertThat(emptyState.walletId()).isEmpty();
        assertThat(emptyState.owner()).isEmpty();
        assertThat(emptyState.balance()).isEqualTo(0);
        assertThat(emptyState.createdAt()).isNull();
        assertThat(emptyState.updatedAt()).isNull();
        assertThat(emptyState.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Should accept WalletState with null wallet ID")
    void shouldAcceptWalletStateWithNullWalletId() {
        // When
        WalletState walletState = new WalletState(
            null, "John Doe", 1000, Instant.now(), Instant.now()
        );

        // Then
        assertThat(walletState.walletId()).isNull();
    }

    @Test
    @DisplayName("Should accept WalletState with null owner")
    void shouldAcceptWalletStateWithNullOwner() {
        // When
        WalletState walletState = new WalletState(
            "wallet-123", null, 1000, Instant.now(), Instant.now()
        );

        // Then
        assertThat(walletState.owner()).isNull();
    }

    @Test
    @DisplayName("Should accept WalletState with negative balance")
    void shouldAcceptWalletStateWithNegativeBalance() {
        // When
        WalletState walletState = new WalletState(
            "wallet-123", "John Doe", -100, Instant.now(), Instant.now()
        );

        // Then
        assertThat(walletState.balance()).isEqualTo(-100);
    }

    @Test
    @DisplayName("Should accept WalletState with zero balance")
    void shouldAcceptWalletStateWithZeroBalance() {
        // When
        WalletState walletState = new WalletState(
            "wallet-123", "John Doe", 0, Instant.now(), Instant.now()
        );

        // Then
        assertThat(walletState.balance()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should validate WalletState withBalance method")
    void shouldValidateWalletStateWithBalanceMethod() {
        // Given
        WalletState originalState = new WalletState(
            "wallet-123", "John Doe", 1000, Instant.now(), Instant.now()
        );
        int newBalance = 1500;
        Instant newUpdatedAt = Instant.now();

        // When
        WalletState updatedState = originalState.withBalance(newBalance, newUpdatedAt);

        // Then
        assertThat(updatedState.walletId()).isEqualTo(originalState.walletId());
        assertThat(updatedState.owner()).isEqualTo(originalState.owner());
        assertThat(updatedState.balance()).isEqualTo(newBalance);
        assertThat(updatedState.createdAt()).isEqualTo(originalState.createdAt());
        assertThat(updatedState.updatedAt()).isEqualTo(newUpdatedAt);
        assertThat(updatedState).isNotSameAs(originalState);
    }

    @Test
    @DisplayName("Should accept WalletState withBalance with negative balance")
    void shouldAcceptWalletStateWithBalanceWithNegativeBalance() {
        // Given
        WalletState originalState = new WalletState(
            "wallet-123", "John Doe", 1000, Instant.now(), Instant.now()
        );

        // When
        WalletState updatedState = originalState.withBalance(-100, Instant.now());

        // Then
        assertThat(updatedState.balance()).isEqualTo(-100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    @DisplayName("Should reject WalletOpened with whitespace-only wallet ID")
    void shouldRejectWalletOpenedWithWhitespaceOnlyWalletId(String whitespaceId) {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of(whitespaceId, "John Doe", 1000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    @DisplayName("Should reject WalletOpened with whitespace-only owner")
    void shouldRejectWalletOpenedWithWhitespaceOnlyOwner(String whitespaceOwner) {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of("wallet-123", whitespaceOwner, 1000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, -100, -1000})
    @DisplayName("Should reject WalletOpened with negative initial balance")
    void shouldRejectWalletOpenedWithNegativeInitialBalance(int negativeBalance) {
        // When & Then
        assertThatThrownBy(() -> WalletOpened.of("wallet-123", "John Doe", negativeBalance))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, 1000, Integer.MAX_VALUE})
    @DisplayName("Should accept WalletOpened with valid initial balance")
    void shouldAcceptWalletOpenedWithValidInitialBalance(int validBalance) {
        // When
        WalletOpened walletOpened = WalletOpened.of("wallet-123", "John Doe", validBalance);

        // Then
        assertThat(walletOpened.initialBalance()).isEqualTo(validBalance);
    }

    @Test
    @DisplayName("Should validate WalletOpened with maximum allowed balance")
    void shouldValidateWalletOpenedWithMaximumAllowedBalance() {
        // When
        WalletOpened walletOpened = WalletOpened.of("wallet-123", "John Doe", Integer.MAX_VALUE);

        // Then
        assertThat(walletOpened.initialBalance()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should validate MoneyTransferred with maximum allowed amount")
    void shouldValidateMoneyTransferredWithMaximumAllowedAmount() {
        // When
        MoneyTransferred transfer = MoneyTransferred.of(
            "transfer-123", "wallet-1", "wallet-2", Integer.MAX_VALUE, 
            Integer.MAX_VALUE, Integer.MAX_VALUE, "Test"
        );

        // Then
        assertThat(transfer.amount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should validate DepositMade with maximum allowed amount")
    void shouldValidateDepositMadeWithMaximumAllowedAmount() {
        // When
        DepositMade deposit = DepositMade.of(
            "deposit-123", "wallet-1", Integer.MAX_VALUE, Integer.MAX_VALUE, "Test"
        );

        // Then
        assertThat(deposit.amount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should validate WithdrawalMade with maximum allowed amount")
    void shouldValidateWithdrawalMadeWithMaximumAllowedAmount() {
        // When
        WithdrawalMade withdrawal = WithdrawalMade.of(
            "withdrawal-123", "wallet-1", Integer.MAX_VALUE, Integer.MAX_VALUE, "Test"
        );

        // Then
        assertThat(withdrawal.amount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should validate WalletState with maximum allowed balance")
    void shouldValidateWalletStateWithMaximumAllowedBalance() {
        // When
        WalletState walletState = new WalletState(
            "wallet-123", "John Doe", Integer.MAX_VALUE, Instant.now(), Instant.now()
        );

        // Then
        assertThat(walletState.balance()).isEqualTo(Integer.MAX_VALUE);
    }
}