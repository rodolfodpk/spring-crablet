package com.crablet.examples.wallet.domain;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.Tag;
import com.crablet.examples.wallet.domain.WalletQueryPatterns;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WalletQueryPatterns.
 * <p>
 * These tests verify that domain-specific query patterns return correct QueryItems
 * for wallet operations according to DCB principles.
 */
class WalletQueryPatternsTest {

    @Test
    void singleWalletDecisionModel_shouldCombineBalanceAndTransfers() {
        // When
        Query query = WalletQueryPatterns.singleWalletDecisionModel("w1");

        // Then
        assertThat(query.items()).hasSize(3);

        // First item: balance events
        assertThat(query.items().get(0).eventTypes())
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(0).tags()).containsExactly(new Tag("wallet_id", "w1"));

        // Second item: transfers from wallet
        assertThat(query.items().get(1).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(1).tags()).containsExactly(new Tag("from_wallet_id", "w1"));

        // Third item: transfers to wallet
        assertThat(query.items().get(2).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(2).tags()).containsExactly(new Tag("to_wallet_id", "w1"));
    }

    @Test
    void transferDecisionModel_shouldIncludeBothWallets() {
        // When
        Query query = WalletQueryPatterns.transferDecisionModel("w1", "w2");

        // Then
        assertThat(query.items()).hasSize(6);

        // Items for wallet w1
        assertThat(query.items().get(0).eventTypes())
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(0).tags()).containsExactly(new Tag("wallet_id", "w1"));

        // MoneyTransferred FROM w1
        assertThat(query.items().get(1).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(1).tags()).containsExactly(new Tag("from_wallet_id", "w1"));

        // MoneyTransferred TO w1
        assertThat(query.items().get(2).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(2).tags()).containsExactly(new Tag("to_wallet_id", "w1"));

        // Items for wallet w2
        assertThat(query.items().get(3).eventTypes())
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(3).tags()).containsExactly(new Tag("wallet_id", "w2"));

        // MoneyTransferred FROM w2
        assertThat(query.items().get(4).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(4).tags()).containsExactly(new Tag("from_wallet_id", "w2"));

        // MoneyTransferred TO w2
        assertThat(query.items().get(5).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(5).tags()).containsExactly(new Tag("to_wallet_id", "w2"));
    }

    @Test
    void walletExistenceQuery_shouldOnlyCheckWalletOpened() {
        // When
        Query query = WalletQueryPatterns.walletExistenceQuery("w1");

        // Then
        assertThat(query.items()).hasSize(1);
        assertThat(query.items().get(0).eventTypes()).containsExactly("WalletOpened");
        assertThat(query.items().get(0).tags()).containsExactly(new Tag("wallet_id", "w1"));
    }

    @Test
    void transferDecisionModel_shouldWorkWithSameWallet() {
        // When (transfer to self)
        Query query = WalletQueryPatterns.transferDecisionModel("w1", "w1");

        // Then - should have 6 items (2 balance + 4 transfer items, with some duplication for same wallet)
        assertThat(query.items()).hasSize(6);
        
        // First balance item for w1
        assertThat(query.items().get(0).eventTypes())
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(0).tags()).containsExactly(new Tag("wallet_id", "w1"));

        // MoneyTransferred FROM w1
        assertThat(query.items().get(1).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(1).tags()).containsExactly(new Tag("from_wallet_id", "w1"));

        // MoneyTransferred TO w1
        assertThat(query.items().get(2).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(2).tags()).containsExactly(new Tag("to_wallet_id", "w1"));

        // Second balance item for w1 (duplicate)
        assertThat(query.items().get(3).eventTypes())
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(3).tags()).containsExactly(new Tag("wallet_id", "w1"));

        // MoneyTransferred FROM w1 (duplicate)
        assertThat(query.items().get(4).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(4).tags()).containsExactly(new Tag("from_wallet_id", "w1"));

        // MoneyTransferred TO w1 (duplicate)
        assertThat(query.items().get(5).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(5).tags()).containsExactly(new Tag("to_wallet_id", "w1"));
    }

    @Test
    void singleWalletDecisionModel_shouldSupportDifferentWalletIds() {
        // When
        Query query1 = WalletQueryPatterns.singleWalletDecisionModel("wallet-abc");
        Query query2 = WalletQueryPatterns.singleWalletDecisionModel("wallet-xyz");

        // Then - queries should be different
        assertThat(query1.items().get(0).tags().get(0).value()).isEqualTo("wallet-abc");
        assertThat(query2.items().get(0).tags().get(0).value()).isEqualTo("wallet-xyz");
        assertThat(query1).isNotEqualTo(query2);
    }
}

