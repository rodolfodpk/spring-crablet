package crablet.unit.core;

import com.crablet.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QueryBuilder.
 */
class QueryBuilderTest {
    
    @Test
    void shouldCreateSimpleQueryWithSingleMatch() {
        // Given
        String[] eventTypes = {"WalletOpened"};
        Tag tag = new Tag("wallet_id", "w1");
        
        // When
        Query query = QueryBuilder.create()
            .matching(eventTypes, tag)
            .build();
        
        // Then
        assertThat(query.items()).hasSize(1);
        assertThat(query.items().get(0).eventTypes()).containsExactly("WalletOpened");
        assertThat(query.items().get(0).tags()).containsExactly(tag);
    }
    
    @Test
    void shouldCreateQueryWithMultipleMatches() {
        // Given
        String[] balanceEvents = {"WalletOpened", "DepositMade", "WithdrawalMade"};
        String[] transferEvents = {"MoneyTransferred"};
        Tag walletTag = new Tag("wallet_id", "w1");
        Tag fromTag = new Tag("from_wallet_id", "w1");
        
        // When
        Query query = QueryBuilder.create()
            .matching(balanceEvents, walletTag)
            .matching(transferEvents, fromTag)
            .build();
        
        // Then
        assertThat(query.items()).hasSize(2);
        assertThat(query.items().get(0).eventTypes()).containsExactly("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(query.items().get(0).tags()).containsExactly(walletTag);
        assertThat(query.items().get(1).eventTypes()).containsExactly("MoneyTransferred");
        assertThat(query.items().get(1).tags()).containsExactly(fromTag);
    }
    
    @Test
    void shouldAddQueryItemsList() {
        // Given
        List<QueryItem> items = List.of(
            QueryItem.of(List.of("Event1"), List.of(new Tag("key1", "val1"))),
            QueryItem.of(List.of("Event2"), List.of(new Tag("key2", "val2")))
        );
        
        // When
        Query query = QueryBuilder.create()
            .matching(items)
            .build();
        
        // Then
        assertThat(query.items()).hasSize(2);
        assertThat(query.items()).isEqualTo(items);
    }
    
    @Test
    void shouldConvertToAppendConditionBuilder() {
        // Given
        Query query = QueryBuilder.create()
            .matching(new String[]{"WalletOpened"}, new Tag("wallet_id", "w1"))
            .build();
        Cursor cursor = Cursor.zero();
        
        // When
        AppendConditionBuilder builder = query.toAppendCondition(cursor);
        
        // Then
        assertThat(builder).isNotNull();
    }
    
    @Test
    void shouldBuildAppendConditionWithIdempotencyCheck() {
        // Given
        Query decisionModel = QueryBuilder.create()
            .matching(new String[]{"WalletOpened", "DepositMade"}, new Tag("wallet_id", "w1"))
            .build();
        Cursor cursor = Cursor.zero();
        
        // When
        AppendCondition condition = decisionModel
            .toAppendCondition(cursor)
            .withIdempotencyCheck("DepositMade", new Tag("deposit_id", "d1"))
            .build();
        
        // Then
        assertThat(condition.afterCursor()).isEqualTo(cursor);
        assertThat(condition.failIfEventsMatch().items()).hasSize(2); // decision model + idempotency
        // First item: decision model
        assertThat(condition.failIfEventsMatch().items().get(0).eventTypes())
            .containsExactly("WalletOpened", "DepositMade");
        assertThat(condition.failIfEventsMatch().items().get(0).tags())
            .containsExactly(new Tag("wallet_id", "w1"));
        // Second item: idempotency check
        assertThat(condition.failIfEventsMatch().items().get(1).eventTypes())
            .containsExactly("DepositMade");
        assertThat(condition.failIfEventsMatch().items().get(1).tags())
            .containsExactly(new Tag("deposit_id", "d1"));
    }
    
    @Test
    void shouldCreateEmptyQuery() {
        // When
        Query query = QueryBuilder.create().build();
        
        // Then
        assertThat(query.items()).isEmpty();
    }
    
    @Test
    void shouldChainMultipleOperations() {
        // Given
        String[] event1 = {"Event1"};
        String[] event2 = {"Event2"};
        String[] event3 = {"Event3"};
        Tag tag1 = new Tag("k1", "v1");
        Tag tag2 = new Tag("k2", "v2");
        Tag tag3 = new Tag("k3", "v3");
        
        // When
        Query query = QueryBuilder.create()
            .matching(event1, tag1)
            .matching(event2, tag2)
            .matching(event3, tag3)
            .build();
        
        // Then
        assertThat(query.items()).hasSize(3);
    }
}

