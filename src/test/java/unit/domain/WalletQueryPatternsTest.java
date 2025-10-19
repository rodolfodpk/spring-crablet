package unit.domain;

import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.Tag;
import com.wallets.domain.WalletQueryPatterns;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WalletQueryPatterns.
 * 
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
        assertThat(query.items()).hasSize(4);
        
        // Items for wallet w1
        assertThat(query.items().get(0).eventTypes())
            .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(0).tags()).containsExactly(new Tag("wallet_id", "w1"));
        
        // MoneyTransferred for w1 (both from and to tags in one item)
        assertThat(query.items().get(1).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(1).tags()).containsExactlyInAnyOrder(
            new Tag("from_wallet_id", "w1"),
            new Tag("to_wallet_id", "w1")
        );
        
        // Items for wallet w2
        assertThat(query.items().get(2).eventTypes())
            .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(2).tags()).containsExactly(new Tag("wallet_id", "w2"));
        
        // MoneyTransferred for w2 (both from and to tags in one item)
        assertThat(query.items().get(3).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(3).tags()).containsExactlyInAnyOrder(
            new Tag("from_wallet_id", "w2"),
            new Tag("to_wallet_id", "w2")
        );
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
        
        // Then - should have 4 items (2 balance + 2 transfer items, but w1=w1 so some duplication)
        assertThat(query.items()).hasSize(4);
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

