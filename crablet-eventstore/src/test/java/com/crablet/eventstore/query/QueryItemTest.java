package com.crablet.eventstore.query;

import com.crablet.eventstore.store.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for QueryItem value object.
 * Tests all factory methods, equality/hashCode, and edge cases.
 */
@DisplayName("QueryItem Unit Tests")
class QueryItemTest {

    @Test
    @DisplayName("Should create QueryItem with event types and tags")
    void shouldCreateQueryItem_WithEventTypesAndTags() {
        // Given
        List<String> eventTypes = List.of("WalletOpened", "DepositMade");
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));

        // When
        QueryItem item = QueryItem.of(eventTypes, tags);

        // Then
        assertThat(item.eventTypes()).isEqualTo(eventTypes);
        assertThat(item.tags()).isEqualTo(tags);
        assertThat(item.eventTypes()).containsExactly("WalletOpened", "DepositMade");
        assertThat(item.tags()).containsExactly(new Tag("wallet_id", "wallet-123"));
    }

    @Test
    @DisplayName("Should create QueryItem with event types only")
    void shouldCreateQueryItem_WithEventTypesOnly() {
        // Given
        List<String> eventTypes = List.of("WalletOpened", "DepositMade", "WithdrawalMade");

        // When
        QueryItem item = QueryItem.ofTypes(eventTypes);

        // Then
        assertThat(item.eventTypes()).isEqualTo(eventTypes);
        assertThat(item.tags()).isEmpty();
        assertThat(item.eventTypes()).containsExactly("WalletOpened", "DepositMade", "WithdrawalMade");
    }

    @Test
    @DisplayName("Should create QueryItem with tags only")
    void shouldCreateQueryItem_WithTagsOnly() {
        // Given
        List<Tag> tags = List.of(
                new Tag("wallet_id", "wallet-123"),
                new Tag("user_id", "user-456")
        );

        // When
        QueryItem item = QueryItem.ofTags(tags);

        // Then
        assertThat(item.eventTypes()).isEmpty();
        assertThat(item.tags()).isEqualTo(tags);
        assertThat(item.tags()).containsExactly(
                new Tag("wallet_id", "wallet-123"),
                new Tag("user_id", "user-456")
        );
    }

    @Test
    @DisplayName("Should create QueryItem with single event type")
    void shouldCreateQueryItem_WithSingleEventType() {
        // Given
        String eventType = "WalletOpened";

        // When
        QueryItem item = QueryItem.ofType(eventType);

        // Then
        assertThat(item.eventTypes()).containsExactly(eventType);
        assertThat(item.tags()).isEmpty();
    }

    @Test
    @DisplayName("Should create QueryItem with single tag")
    void shouldCreateQueryItem_WithSingleTag() {
        // Given
        Tag tag = new Tag("wallet_id", "wallet-123");

        // When
        QueryItem item = QueryItem.ofTag(tag);

        // Then
        assertThat(item.eventTypes()).isEmpty();
        assertThat(item.tags()).containsExactly(tag);
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        List<String> eventTypes = List.of("WalletOpened");
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));

        // When
        QueryItem item1 = QueryItem.of(eventTypes, tags);
        QueryItem item2 = QueryItem.of(eventTypes, tags);

        // Then
        assertThat(item1).isEqualTo(item2);
        assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different event types")
    void shouldImplementEquals_WithDifferentEventTypes() {
        // Given
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));

        // When
        QueryItem item1 = QueryItem.of(List.of("WalletOpened"), tags);
        QueryItem item2 = QueryItem.of(List.of("DepositMade"), tags);

        // Then
        assertThat(item1).isNotEqualTo(item2);
    }

    @Test
    @DisplayName("Should implement equals with different tags")
    void shouldImplementEquals_WithDifferentTags() {
        // Given
        List<String> eventTypes = List.of("WalletOpened");

        // When
        QueryItem item1 = QueryItem.of(eventTypes, List.of(new Tag("wallet_id", "wallet-123")));
        QueryItem item2 = QueryItem.of(eventTypes, List.of(new Tag("wallet_id", "wallet-456")));

        // Then
        assertThat(item1).isNotEqualTo(item2);
    }

    @Test
    @DisplayName("Should handle empty event types list")
    void shouldHandleEmptyEventTypesList() {
        // Given
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));

        // When
        QueryItem item = QueryItem.of(List.of(), tags);

        // Then
        assertThat(item.eventTypes()).isEmpty();
        assertThat(item.tags()).isEqualTo(tags);
    }

    @Test
    @DisplayName("Should handle empty tags list")
    void shouldHandleEmptyTagsList() {
        // Given
        List<String> eventTypes = List.of("WalletOpened");

        // When
        QueryItem item = QueryItem.of(eventTypes, List.of());

        // Then
        assertThat(item.eventTypes()).isEqualTo(eventTypes);
        assertThat(item.tags()).isEmpty();
    }

    @Test
    @DisplayName("Should handle both empty lists")
    void shouldHandleBothEmptyLists() {
        // When
        QueryItem item = QueryItem.of(List.of(), List.of());

        // Then
        assertThat(item.eventTypes()).isEmpty();
        assertThat(item.tags()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null event types - record allows null")
    void shouldHandleNullEventTypes_RecordAllowsNull() {
        // Given - Records allow null fields unless validated
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));

        // When & Then - Record constructor allows null
        QueryItem item = new QueryItem(null, tags);
        assertThat(item.eventTypes()).isNull();
        assertThat(item.tags()).isEqualTo(tags);
    }

    @Test
    @DisplayName("Should handle null tags - record allows null")
    void shouldHandleNullTags_RecordAllowsNull() {
        // Given - Records allow null fields unless validated
        List<String> eventTypes = List.of("WalletOpened");

        // When & Then - Record constructor allows null
        QueryItem item = new QueryItem(eventTypes, null);
        assertThat(item.eventTypes()).isEqualTo(eventTypes);
        assertThat(item.tags()).isNull();
    }

    @Test
    @DisplayName("Should handle multiple event types and tags")
    void shouldHandleMultipleEventTypesAndTags() {
        // Given
        List<String> eventTypes = List.of("WalletOpened", "DepositMade", "WithdrawalMade");
        List<Tag> tags = List.of(
                new Tag("wallet_id", "wallet-123"),
                new Tag("user_id", "user-456"),
                new Tag("transaction_id", "tx-789")
        );

        // When
        QueryItem item = QueryItem.of(eventTypes, tags);

        // Then
        assertThat(item.eventTypes()).hasSize(3);
        assertThat(item.tags()).hasSize(3);
        assertThat(item.eventTypes()).containsExactly("WalletOpened", "DepositMade", "WithdrawalMade");
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString_Correctly() {
        // Given
        List<String> eventTypes = List.of("WalletOpened");
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));

        // When
        QueryItem item = QueryItem.of(eventTypes, tags);

        // Then - Record auto-generates toString() in format "RecordName[field1=value1, field2=value2]"
        String toString = item.toString();
        assertThat(toString)
                .contains("QueryItem[")
                .contains("eventTypes=")
                .contains("tags=");
    }
}

