package com.crablet.wallet.domain;

import com.crablet.wallet.domain.WalletTags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WalletTags constants to ensure they work correctly.
 */
class WalletTagsTest {

    @Test
    void shouldHaveCorrectTagNames() {
        // Verify that the constants have the expected values
        assertThat(WalletTags.WALLET_ID).isEqualTo("wallet_id");
        assertThat(WalletTags.DEPOSIT_ID).isEqualTo("deposit_id");
        assertThat(WalletTags.WITHDRAWAL_ID).isEqualTo("withdrawal_id");
        assertThat(WalletTags.TRANSFER_ID).isEqualTo("transfer_id");
        assertThat(WalletTags.FROM_WALLET_ID).isEqualTo("from_wallet_id");
        assertThat(WalletTags.TO_WALLET_ID).isEqualTo("to_wallet_id");
    }

    @Test
    void shouldPreventTypos() {
        // This test demonstrates how constants prevent typos
        // If we used string literals, we might accidentally write:
        // .tag("wallet-id", walletId)  // Wrong: uses hyphen instead of underscore
        // .tag("walletId", walletId)   // Wrong: uses camelCase instead of snake_case
        
        // With constants, the IDE will catch typos at compile time:
        assertThat(WalletTags.WALLET_ID).isNotEqualTo("wallet-id");
        assertThat(WalletTags.WALLET_ID).isNotEqualTo("walletId");
        assertThat(WalletTags.WALLET_ID).isNotEqualTo("WALLET_ID"); // Case sensitive
    }

    @Test
    void shouldEnableRefactoring() {
        // If we ever need to change tag names, we only need to update the constants
        // This test ensures that all tag names follow a consistent pattern
        
        // All tag names should use snake_case
        assertThat(WalletTags.WALLET_ID).matches("^[a-z_]+$");
        assertThat(WalletTags.DEPOSIT_ID).matches("^[a-z_]+$");
        assertThat(WalletTags.WITHDRAWAL_ID).matches("^[a-z_]+$");
        assertThat(WalletTags.TRANSFER_ID).matches("^[a-z_]+$");
        assertThat(WalletTags.FROM_WALLET_ID).matches("^[a-z_]+$");
        assertThat(WalletTags.TO_WALLET_ID).matches("^[a-z_]+$");
    }
}
