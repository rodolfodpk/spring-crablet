package com.crablet.eventstore.query;

import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.SequenceNumber;
import com.crablet.eventstore.store.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QuerySqlBuilderImpl.
 * Tests SQL WHERE clause generation for all query patterns, cursor handling, and parameter collection.
 */
@DisplayName("QuerySqlBuilderImpl Unit Tests")
class QuerySqlBuilderImplTest {

    private final QuerySqlBuilderImpl sqlBuilder = new QuerySqlBuilderImpl();

    @Test
    @DisplayName("Should build WHERE clause with empty query and null cursor")
    void shouldBuildWhereClause_WithEmptyQueryAndNullCursor() {
        // Given
        Query query = Query.empty();
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then
        assertThat(whereClause).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("Should build WHERE clause with empty query and zero cursor")
    void shouldBuildWhereClause_WithEmptyQueryAndZeroCursor() {
        // Given
        Query query = Query.empty();
        Cursor cursor = Cursor.zero();
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Zero cursor position means > 0 check fails, so no condition
        assertThat(whereClause).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("Should build WHERE clause with empty query and non-zero cursor")
    void shouldBuildWhereClause_WithEmptyQueryAndNonZeroCursor() {
        // Given
        Query query = Query.empty();
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Only position condition
        assertThat(whereClause).isEqualTo("position > ?");
        assertThat(params).hasSize(1);
        assertThat(params.get(0)).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should build WHERE clause with event types only")
    void shouldBuildWhereClause_WithEventTypesOnly() {
        // Given
        Query query = Query.of(QueryItem.ofTypes(List.of("WalletOpened", "DepositMade")));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Single query item still wrapped in OR parentheses
        assertThat(whereClause).isEqualTo("((type = ANY(?)))");
        assertThat(params).hasSize(1);
        assertThat(params.get(0)).isInstanceOf(String[].class);
        String[] eventTypes = (String[]) params.get(0);
        assertThat(eventTypes).containsExactly("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should build WHERE clause with tags only")
    void shouldBuildWhereClause_WithTagsOnly() {
        // Given
        Query query = Query.of(QueryItem.ofTags(List.of(new Tag("wallet_id", "wallet-123"))));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Single query item still wrapped in OR parentheses
        assertThat(whereClause).isEqualTo("((tags @> ?::text[]))");
        assertThat(params).hasSize(1);
        assertThat(params.get(0)).isInstanceOf(String[].class);
        String[] tagStrings = (String[]) params.get(0);
        assertThat(tagStrings).containsExactly("wallet_id=wallet-123");
    }

    @Test
    @DisplayName("Should build WHERE clause with event types and tags")
    void shouldBuildWhereClause_WithEventTypesAndTags() {
        // Given
        Query query = Query.of(QueryItem.of(
                List.of("WalletOpened"),
                List.of(new Tag("wallet_id", "wallet-123"))
        ));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Single query item still wrapped in OR parentheses
        assertThat(whereClause).isEqualTo("((type = ANY(?) AND tags @> ?::text[]))");
        assertThat(params).hasSize(2);
        assertThat(params.get(0)).isInstanceOf(String[].class);
        assertThat(params.get(1)).isInstanceOf(String[].class);
        
        String[] eventTypes = (String[]) params.get(0);
        assertThat(eventTypes).containsExactly("WalletOpened");
        
        String[] tagStrings = (String[]) params.get(1);
        assertThat(tagStrings).containsExactly("wallet_id=wallet-123");
    }

    @Test
    @DisplayName("Should build WHERE clause with multiple query items")
    void shouldBuildWhereClause_WithMultipleQueryItems() {
        // Given
        Query query = Query.of(List.of(
                QueryItem.ofType("WalletOpened"),
                QueryItem.ofType("DepositMade")
        ));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - OR conditions
        assertThat(whereClause).isEqualTo("((type = ANY(?)) OR (type = ANY(?)))");
        assertThat(params).hasSize(2);
        
        String[] eventTypes1 = (String[]) params.get(0);
        assertThat(eventTypes1).containsExactly("WalletOpened");
        
        String[] eventTypes2 = (String[]) params.get(1);
        assertThat(eventTypes2).containsExactly("DepositMade");
    }

    @Test
    @DisplayName("Should build WHERE clause with cursor and query")
    void shouldBuildWhereClause_WithCursorAndQuery() {
        // Given
        Query query = Query.of(QueryItem.ofType("WalletOpened"));
        Cursor cursor = Cursor.of(SequenceNumber.of(50L), Instant.now(), "tx-123");
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Combined conditions (single query item wrapped in OR parentheses)
        assertThat(whereClause).isEqualTo("position > ? AND ((type = ANY(?)))");
        assertThat(params).hasSize(2);
        assertThat(params.get(0)).isEqualTo(50L);
        assertThat(params.get(1)).isInstanceOf(String[].class);
    }

    @Test
    @DisplayName("Should collect parameters correctly")
    void shouldCollectParameters_Correctly() {
        // Given
        Query query = Query.of(QueryItem.of(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", "wallet-123"), new Tag("user_id", "user-456"))
        ));
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        List<Object> params = new ArrayList<>();

        // When
        sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Verify parameter order and types
        assertThat(params).hasSize(3);
        assertThat(params.get(0)).isEqualTo(100L); // Cursor position
        
        String[] eventTypes = (String[]) params.get(1);
        assertThat(eventTypes).containsExactly("WalletOpened", "DepositMade");
        
        String[] tagStrings = (String[]) params.get(2);
        assertThat(tagStrings).containsExactly("wallet_id=wallet-123", "user_id=user-456");
    }

    @Test
    @DisplayName("Should build WHERE clause with empty event types")
    void shouldBuildWhereClause_WithEmptyEventTypes() {
        // Given
        Query query = Query.of(QueryItem.ofTags(List.of(new Tag("wallet_id", "wallet-123"))));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Only tags condition (single query item wrapped in OR parentheses)
        assertThat(whereClause).isEqualTo("((tags @> ?::text[]))");
        assertThat(params).hasSize(1);
    }

    @Test
    @DisplayName("Should build WHERE clause with empty tags")
    void shouldBuildWhereClause_WithEmptyTags() {
        // Given
        Query query = Query.of(QueryItem.ofTypes(List.of("WalletOpened")));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Only event types condition (single query item wrapped in OR parentheses)
        assertThat(whereClause).isEqualTo("((type = ANY(?)))");
        assertThat(params).hasSize(1);
    }

    @Test
    @DisplayName("Should build WHERE clause with query item having both empty")
    void shouldBuildWhereClause_WithQueryItemHavingBothEmpty() {
        // Given
        Query query = Query.of(QueryItem.of(List.of(), List.of()));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Empty condition is skipped (length <= 2 check)
        assertThat(whereClause).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("Should handle null cursor correctly")
    void shouldHandleNullCursor_Correctly() {
        // Given
        Query query = Query.of(QueryItem.ofType("WalletOpened"));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Null cursor is treated as position 0 (no position condition), single query item wrapped
        assertThat(whereClause).isEqualTo("((type = ANY(?)))");
        assertThat(params).hasSize(1);
        assertThat(params).doesNotContain(0L); // No position parameter
    }

    @Test
    @DisplayName("Should build WHERE clause with query item having empty condition - should skip")
    void shouldBuildWhereClause_WithQueryItemHavingEmptyCondition_ShouldSkip() {
        // Given - QueryItem with both empty (will be skipped)
        Query query = Query.of(List.of(
                QueryItem.of(List.of(), List.of()), // Empty - will be skipped
                QueryItem.ofType("WalletOpened")   // Valid - will be included
        ));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Only the valid query item is included (wrapped in OR parentheses)
        assertThat(whereClause).isEqualTo("((type = ANY(?)))");
        assertThat(params).hasSize(1);
    }

    @Test
    @DisplayName("Should build WHERE clause with multiple tags")
    void shouldBuildWhereClause_WithMultipleTags() {
        // Given
        Query query = Query.of(QueryItem.of(
                List.of("WalletOpened"),
                List.of(
                        new Tag("wallet_id", "wallet-123"),
                        new Tag("user_id", "user-456"),
                        new Tag("transaction_id", "tx-789")
                )
        ));
        Cursor cursor = null;
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Single query item wrapped in OR parentheses
        assertThat(whereClause).isEqualTo("((type = ANY(?) AND tags @> ?::text[]))");
        assertThat(params).hasSize(2);
        
        String[] tagStrings = (String[]) params.get(1);
        assertThat(tagStrings).containsExactly(
                "wallet_id=wallet-123",
                "user_id=user-456",
                "transaction_id=tx-789"
        );
    }

    @Test
    @DisplayName("Should build WHERE clause with cursor at zero and non-empty query")
    void shouldBuildWhereClause_WithCursorAtZeroAndNonEmptyQuery() {
        // Given
        Query query = Query.of(QueryItem.ofType("WalletOpened"));
        Cursor cursor = Cursor.zero();
        List<Object> params = new ArrayList<>();

        // When
        String whereClause = sqlBuilder.buildWhereClause(query, cursor, params);

        // Then - Zero cursor means no position condition (single query item wrapped)
        assertThat(whereClause).isEqualTo("((type = ANY(?)))");
        assertThat(params).hasSize(1);
        assertThat(params).doesNotContain(0L);
    }
}

