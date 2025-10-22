package crablet.integration;
import static wallets.testutils.DCBTestHelpers.*;

import com.crablet.core.AppendEvent;
import com.crablet.core.Cursor;
import com.crablet.core.EventStoreException;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.crablet.core.impl.JDBCEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import crablet.integration.AbstractCrabletTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for JDBCEventStore query building edge cases.
 * Tests complex query scenarios that contribute to branch coverage in buildEventQueryWhereClause().
 */
@DisplayName("JDBCEventStore Query Edge Cases Tests")
class JDBCEventStoreQueryIT extends AbstractCrabletTest {

    @Autowired
    private JDBCEventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        jdbcTemplate.execute("DELETE FROM events");
        jdbcTemplate.execute("DELETE FROM commands");
        
        // Insert test events for querying
        insertTestEvents();
    }

    private void insertTestEvents() {
        // Insert events with different types and tags for testing
        AppendEvent event1 = AppendEvent.builder("WalletOpened")
            .tag("wallet", "wallet-1")
            .tag("owner", "alice")
            .data("{\"walletId\":\"wallet-1\",\"owner\":\"alice\"}")
            .build();
        
        AppendEvent event2 = AppendEvent.builder("DepositMade")
            .tag("wallet", "wallet-1")
            .tag("amount", "100")
            .data("{\"walletId\":\"wallet-1\",\"amount\":100}")
            .build();
        
        AppendEvent event3 = AppendEvent.builder("WalletOpened")
            .tag("wallet", "wallet-2")
            .tag("owner", "bob")
            .data("{\"walletId\":\"wallet-2\",\"owner\":\"bob\"}")
            .build();
        
        AppendEvent event4 = AppendEvent.builder("TransferMade")
            .tag("from", "wallet-1")
            .tag("to", "wallet-2")
            .tag("amount", "50")
            .data("{\"fromWallet\":\"wallet-1\",\"toWallet\":\"wallet-2\",\"amount\":50}")
            .build();

        eventStore.append(List.of(event1, event2, event3, event4));
    }

    @Test
    @DisplayName("Should handle query with empty items")
    void shouldHandleQueryWithEmptyItems() {
        // Given: query with empty items list
        Query emptyQuery = Query.of(List.of());
        Cursor cursor = Cursor.zero();

        // When: query called
        List<StoredEvent> events = eventStore.query(emptyQuery, cursor);

        // Then: returns all events, no WHERE clause applied
        assertThat(events).hasSize(4);
    }

    @Test
    @DisplayName("Should handle query with null cursor")
    void shouldHandleQueryWithNullCursor() {
        // Given: query with null cursor
        Query query = Query.of(List.of(QueryItem.ofTypes(List.of("WalletOpened"))));

        // When: query called with null cursor
        List<StoredEvent> events = eventStore.query(query, null);

        // Then: returns events from beginning
        assertThat(events).hasSize(2); // 2 WalletOpened events
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
        assertThat(events.get(1).type()).isEqualTo("WalletOpened");
    }

    @Test
    @DisplayName("Should handle query with cursor position")
    void shouldHandleQueryWithCursorPosition() {
        // Given: query with cursor after first event
        Query query = Query.of(List.of(QueryItem.ofTypes(List.of("WalletOpened"))));
        Cursor cursor = Cursor.of(1L); // After first event

        // When: query called with cursor
        List<StoredEvent> events = eventStore.query(query, cursor);

        // Then: returns events after cursor position
        assertThat(events).hasSize(1); // 1 WalletOpened event after position 1
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
    }

    @Test
    @DisplayName("Should combine multiple tag filters")
    void shouldCombineMultipleTagFilters() {
        // Given: query with multiple tag filters
        Query query = Query.of(List.of(
            QueryItem.of(List.of("WalletOpened"), List.of(Tag.of("wallet", "wallet-1")))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: applies all filters with AND logic
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
        assertThat(events.get(0).tags()).contains(Tag.of("wallet", "wallet-1"));
    }

    @Test
    @DisplayName("Should filter by event type and tags")
    void shouldFilterByEventTypeAndTags() {
        // Given: query with event types + tags
        Query query = Query.of(List.of(
            QueryItem.of(List.of("DepositMade"), List.of(Tag.of("wallet", "wallet-1")))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: applies both filters correctly
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("DepositMade");
        assertThat(events.get(0).tags()).contains(Tag.of("wallet", "wallet-1"));
    }

    @Test
    @DisplayName("Should handle query with multiple query items")
    void shouldHandleQueryWithMultipleQueryItems() {
        // Given: query with multiple query items (OR logic between items)
        Query query = Query.of(List.of(
            QueryItem.ofTypes(List.of("WalletOpened")),
            QueryItem.ofTypes(List.of("DepositMade"))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: returns events matching any query item
        assertThat(events).hasSize(3); // 2 WalletOpened + 1 DepositMade
        assertThat(events).extracting(StoredEvent::type)
            .containsExactlyInAnyOrder("WalletOpened", "WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should handle query with complex tag combinations")
    void shouldHandleQueryWithComplexTagCombinations() {
        // Given: query with complex tag combinations
        Query query = Query.of(List.of(
            QueryItem.of(List.of("TransferMade"), 
                List.of(Tag.of("from", "wallet-1"), Tag.of("to", "wallet-2")))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: applies complex tag filters
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("TransferMade");
        assertThat(events.get(0).tags()).contains(Tag.of("from", "wallet-1"));
        assertThat(events.get(0).tags()).contains(Tag.of("to", "wallet-2"));
    }

    @Test
    @DisplayName("Should handle query with no matching events")
    void shouldHandleQueryWithNoMatchingEvents() {
        // Given: query that matches nothing
        Query query = Query.of(List.of(
            QueryItem.ofTypes(List.of("NonExistentEvent"))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: returns empty list
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should handle query with empty event types")
    void shouldHandleQueryWithEmptyEventTypes() {
        // Given: query with empty event types but with tags
        Query query = Query.of(List.of(
            QueryItem.of(List.of(), List.of(Tag.of("wallet", "wallet-1")))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: filters by tags only
        assertThat(events).hasSize(2); // 2 events with wallet-1 tag
        assertThat(events).extracting(StoredEvent::type)
            .containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should handle query with empty tags")
    void shouldHandleQueryWithEmptyTags() {
        // Given: query with event types but empty tags
        Query query = Query.of(List.of(
            QueryItem.of(List.of("WalletOpened"), List.of())
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: filters by event types only
        assertThat(events).hasSize(2); // 2 WalletOpened events
        assertThat(events).extracting(StoredEvent::type)
            .containsExactlyInAnyOrder("WalletOpened", "WalletOpened");
    }

    @Test
    @DisplayName("Should handle queryAsJsonArray with complex query")
    void shouldHandleQueryAsJsonArrayWithComplexQuery() {
        // Given: complex query
        Query query = Query.of(List.of(
            QueryItem.of(List.of("WalletOpened", "DepositMade"), 
                List.of(Tag.of("wallet", "wallet-1")))
        ));

        // When: queryAsJsonArray called
        byte[] result = eventStore.queryAsJsonArray(query, Cursor.zero());

        // Then: returns JSON array with matching events
        String jsonString = new String(result);
        assertThat(jsonString).contains("wallet-1");
        assertThat(jsonString).contains("alice");
        assertThat(jsonString).contains("100");
    }

    @Test
    @DisplayName("Should handle queryAsJsonArray with no results")
    void shouldHandleQueryAsJsonArrayWithNoResults() {
        // Given: query that matches nothing
        Query query = Query.of(List.of(
            QueryItem.ofTypes(List.of("NonExistentEvent"))
        ));

        // When: queryAsJsonArray called
        byte[] result = eventStore.queryAsJsonArray(query, Cursor.zero());

        // Then: returns "[]" as bytes
        assertThat(new String(result)).isEqualTo("[]");
    }

    @Test
    @DisplayName("Should handle queryAsJsonArray with cursor")
    void shouldHandleQueryAsJsonArrayWithCursor() {
        // Given: query with cursor
        Query query = Query.of(List.of(QueryItem.ofTypes(List.of("WalletOpened"))));
        Cursor cursor = Cursor.of(1L); // After first event

        // When: queryAsJsonArray called with cursor
        byte[] result = eventStore.queryAsJsonArray(query, cursor);

        // Then: returns JSON array with events after cursor
        String jsonString = new String(result);
        assertThat(jsonString).contains("wallet-2");
        assertThat(jsonString).contains("bob");
        // Should only contain events after position 1
    }

    @Test
    @DisplayName("Should handle query with null query items")
    void shouldHandleQueryWithNullQueryItems() {
        // Given: query with null items
        Query query = Query.of((List<QueryItem>) null);

        // When & Then: should throw EventStoreException due to null items
        assertThatThrownBy(() -> eventStore.query(query, Cursor.zero()))
                .isInstanceOf(EventStoreException.class)
                .hasMessageContaining("Failed to query events");
    }

    @Test
    @DisplayName("Should handle query with mixed event types and tags")
    void shouldHandleQueryWithMixedEventTypesAndTags() {
        // Given: query with mixed event types and tags
        Query query = Query.of(List.of(
            QueryItem.of(List.of("WalletOpened", "DepositMade"), 
                List.of(Tag.of("wallet", "wallet-1"), Tag.of("owner", "alice")))
        ));

        // When: query called
        List<StoredEvent> events = eventStore.query(query, Cursor.zero());

        // Then: applies mixed filters correctly
        assertThat(events).hasSize(1); // Only WalletOpened with wallet-1 and owner alice
        assertThat(events.get(0).type()).isEqualTo("WalletOpened");
        assertThat(events.get(0).tags()).contains(Tag.of("wallet", "wallet-1"));
        assertThat(events.get(0).tags()).contains(Tag.of("owner", "alice"));
    }
}
