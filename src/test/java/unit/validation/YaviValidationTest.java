package unit.validation;

import com.wallets.features.deposit.DepositCommand;
import com.wallets.features.openwallet.OpenWalletCommand;
import com.wallets.features.transfer.TransferMoneyCommand;
import com.wallets.features.withdraw.WithdrawCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test YAVI validation on commands.
 */
class YaviValidationTest {

    @Test
    @DisplayName("Should create valid deposit command")
    void shouldCreateValidDepositCommand() {
        DepositCommand cmd = new DepositCommand("dep-1", "wallet-1", 100, "test deposit");

        assertThat(cmd.depositId()).isEqualTo("dep-1");
        assertThat(cmd.walletId()).isEqualTo("wallet-1");
        assertThat(cmd.amount()).isEqualTo(100);
        assertThat(cmd.description()).isEqualTo("test deposit");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    @DisplayName("Should reject null/empty deposit ID")
    void shouldRejectInvalidDepositId(String invalidId) {
        assertThatThrownBy(() ->
                new DepositCommand(invalidId, "wallet-1", 100, "test")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depositId");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    @DisplayName("Should reject non-positive amounts")
    void shouldRejectNonPositiveAmount(int amount) {
        assertThatThrownBy(() ->
                new DepositCommand("dep-1", "wallet-1", amount, "test")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should create valid withdraw command")
    void shouldCreateValidWithdrawCommand() {
        WithdrawCommand cmd = new WithdrawCommand("withdraw-1", "wallet-1", 100, "test withdraw");

        assertThat(cmd.withdrawalId()).isEqualTo("withdraw-1");
        assertThat(cmd.walletId()).isEqualTo("wallet-1");
        assertThat(cmd.amount()).isEqualTo(100);
        assertThat(cmd.description()).isEqualTo("test withdraw");
    }

    @Test
    @DisplayName("Should reject negative withdrawal amount")
    void shouldRejectNegativeWithdrawalAmount() {
        assertThatThrownBy(() ->
                new WithdrawCommand("withdraw-1", "wallet-1", -100, "test")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should create valid transfer command")
    void shouldCreateValidTransferCommand() {
        TransferMoneyCommand cmd = new TransferMoneyCommand("transfer-1", "wallet-1", "wallet-2", 100, "test transfer");

        assertThat(cmd.transferId()).isEqualTo("transfer-1");
        assertThat(cmd.fromWalletId()).isEqualTo("wallet-1");
        assertThat(cmd.toWalletId()).isEqualTo("wallet-2");
        assertThat(cmd.amount()).isEqualTo(100);
        assertThat(cmd.description()).isEqualTo("test transfer");
    }

    @Test
    @DisplayName("Should reject transfer to same wallet")
    void shouldRejectTransferToSameWallet() {
        assertThatThrownBy(() ->
                new TransferMoneyCommand("transfer-1", "wallet-1", "wallet-1", 100, "test")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same wallet");
    }

    @Test
    @DisplayName("Should create valid open wallet command")
    void shouldCreateValidOpenWalletCommand() {
        OpenWalletCommand cmd = new OpenWalletCommand("wallet-1", "John Doe", 1000);

        assertThat(cmd.walletId()).isEqualTo("wallet-1");
        assertThat(cmd.owner()).isEqualTo("John Doe");
        assertThat(cmd.initialBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should reject negative initial balance")
    void shouldRejectNegativeInitialBalance() {
        assertThatThrownBy(() ->
                new OpenWalletCommand("wallet-1", "John Doe", -100)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialBalance");
    }
}
