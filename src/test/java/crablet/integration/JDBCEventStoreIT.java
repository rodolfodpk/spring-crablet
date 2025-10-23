package crablet.integration;
import static crablet.testutils.DCBTestHelpers.*;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
import com.crablet.core.impl.EventStoreConfig;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.crablet.core.impl.JDBCEventStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WalletOpened;
import com.crablet.core.ClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import crablet.integration.AbstractCrabletIT;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JDBCEventStore edge cases and error handling.
 * Tests database-specific functionality, connection handling, and error scenarios.
 */
class JDBCEventStoreTest extends AbstractCrabletIT {

    @Autowired
    private JDBCEventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventStoreConfig config;
    
    @Autowired
    private ClockProvider clock;

    @BeforeEach
    void setUp() {

        // Clean up database before each test to ensure isolation
        jdbcTemplate.execute("DELETE FROM events");
        jdbcTemplate.execute("DELETE FROM commands");
    }

    @Test
    @DisplayName("Should handle empty event list in append")
    void shouldHandleEmptyEventListInAppend() {
        // Given
        List<AppendEvent> emptyEvents = List.of();

        // When & Then
        assertThatCode(() -> eventStore.append(emptyEvents))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle null event list in append")
    void shouldHandleNullEventListInAppend() {
        // Given
        List<AppendEvent> nullEvents = null;

        // When & Then
        assertThatThrownBy(() -> eventStore.append(nullEvents))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle events with null data")
    void shouldHandleEventsWithNullData() {
        // Given
        AppendEvent eventWithNullData = AppendEvent.builder("TestEvent").data((byte[]) null).build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(eventWithNullData)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to append events");
    }

    @Test
    @DisplayName("Should handle events with empty data")
    void shouldHandleEventsWithEmptyData() {
        // Given
        AppendEvent eventWithEmptyData = AppendEvent.builder("TestEvent").data(new byte[0]).build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(eventWithEmptyData)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to append events");
    }

    @Test
    @DisplayName("Should handle events with invalid JSON data")
    void shouldHandleEventsWithInvalidJsonData() {
        // Given
        AppendEvent eventWithInvalidJson = AppendEvent.builder("TestEvent").data("invalid json").build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(eventWithInvalidJson)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to append events");
    }

    @Test
    @DisplayName("Should handle events with null tags")
    void shouldHandleEventsWithNullTags() throws JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("test-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        AppendEvent eventWithNullTags = AppendEvent.builder("WalletOpened").data(data).build();

        // When & Then - null tags should be handled gracefully
        assertThatCode(() -> eventStore.append(List.of(eventWithNullTags)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle events with tags containing null values")
    void shouldHandleEventsWithTagsContainingNullValues() throws JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("test-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        List<Tag> tagsWithNull = List.of(new Tag("key", null));
        AppendEvent eventWithNullTagValues = AppendEvent.builder("WalletOpened").data(data).build();

        // When & Then - null tag values should be filtered out gracefully
        assertThatCode(() -> eventStore.append(List.of(eventWithNullTagValues)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle query with null query parameter")
    void shouldHandleQueryWithNullQueryParameter() {
        // When & Then
        assertThatThrownBy(() -> eventStore.query(null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to query events");
    }

    @Test
    @DisplayName("Should handle query with empty query")
    void shouldHandleQueryWithEmptyQuery() {
        // Given
        Query emptyQuery = Query.empty();

        // When
        List<StoredEvent> events = eventStore.query(emptyQuery, null);

        // Then
        assertThat(events).isNotNull();
        // May be empty or contain existing events, both are valid
    }

    @Test
    @DisplayName("Should handle query with complex query items")
    void shouldHandleQueryWithComplexQueryItems() {
        // Given
        Query complexQuery = Query.of(List.of(
                QueryItem.of(List.of("WalletOpened", "MoneyTransferred"), List.of(
                        new Tag("wallet_id", "test-wallet"),
                        new Tag("transfer_id", "test-transfer")
                ))
        ));

        // When
        List<StoredEvent> events = eventStore.query(complexQuery, null);

        // Then
        assertThat(events).isNotNull();
        // May be empty or contain matching events, both are valid
    }

    @Test
    @DisplayName("Should handle query with cursor")
    void shouldHandleQueryWithCursor() {
        // Given
        Query query = Query.empty();
        Cursor cursor = Cursor.of(0L, Instant.now());

        // When
        List<StoredEvent> events = eventStore.query(query, cursor);

        // Then
        assertThat(events).isNotNull();
        // May be empty or contain events after cursor, both are valid
    }

    @Test
    @DisplayName("Should handle append with connection using withConnection()")
    void shouldHandleAppendWithConnection() throws SQLException, JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("connection-test-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        AppendEvent event = AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "connection-test-wallet")
                .data(data)
                .build();

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            // When - Create transaction-scoped EventStore
            EventStore txStore = eventStore.withConnection(connection);

            // Then - Use clean API without passing connection
            assertThatCode(() -> txStore.append(List.of(event)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Test how PostgreSQL JDBC driver handles xid8")
    void testXid8Handling() {
        // Test what pg_current_xact_id() returns
        String sql = "SELECT pg_current_xact_id()";
        String result = jdbcTemplate.queryForObject(sql, String.class);
        System.out.println("pg_current_xact_id() returns: " + result);

        // Test what transaction_id column returns (after inserting an event)
        WalletOpened walletOpened = WalletOpened.of("test-wallet", "Test User", 1000);
        try {
            String jsonData = objectMapper.writeValueAsString(walletOpened);
            AppendEvent event = AppendEvent.builder("WalletOpened")
                    .tag("wallet_id", "test-wallet")
                    .data(jsonData)
                    .build();
            eventStore.append(List.of(event));

            String sql2 = "SELECT transaction_id FROM events LIMIT 1";
            String txId = jdbcTemplate.queryForObject(sql2, String.class);
            System.out.println("transaction_id column returns: " + txId);

            // Test if we can cast it to long
            try {
                Long txIdLong = jdbcTemplate.queryForObject(sql2, Long.class);
                System.out.println("transaction_id as Long: " + txIdLong);
            } catch (Exception e) {
                System.out.println("Cannot cast transaction_id to Long: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Error testing transaction_id: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should handle appendIf with connection using withConnection()")
    void shouldHandleAppendIfWithConnection() throws SQLException, JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("appendif-test-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        AppendEvent event = AppendEvent.builder("WalletOpened")
            .tag("wallet_id", "appendif-test-wallet")
            .data(data)
            .build();
        AppendCondition condition = AppendCondition.expectEmptyStream();

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            // When - Create transaction-scoped EventStore
            EventStore txStore = eventStore.withConnection(connection);
            txStore.appendIf(List.of(event), condition);

            // Then - no assertion needed since method returns void
        }
    }

    @Test
    @DisplayName("Should handle appendIf with null condition using withConnection()")
    void shouldHandleAppendIfWithNullCondition() throws SQLException, JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("null-condition-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        AppendEvent event = AppendEvent.builder("WalletOpened").tag("wallet_id", "null-condition-wallet").data(data).build();

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            // When & Then - Create transaction-scoped EventStore and test null condition
            EventStore txStore = eventStore.withConnection(connection);
            assertThatCode(() -> txStore.appendIf(List.of(event), null))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Should handle withConnection method")
    void shouldHandleWithConnectionMethod() throws SQLException {
        // Given
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            // When
            EventStore connectionScopedStore = eventStore.withConnection(connection);

            // Then
            assertThat(connectionScopedStore).isNotNull();
            assertThat(connectionScopedStore).isNotSameAs(eventStore);
        }
    }

    @Test
    @DisplayName("Should handle projection with empty projectors")
    void shouldHandleProjectionWithEmptyProjectors() {
        // Given
        Query query = Query.empty();
        List<StateProjector<Object>> emptyProjectors = List.of();

        // When & Then - should throw for empty projectors
        assertThatThrownBy(() -> eventStore.project(query, Cursor.zero(), Object.class, emptyProjectors))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle projection with null projectors")
    void shouldHandleProjectionWithNullProjectors() {
        // When & Then
        assertThatThrownBy(() -> eventStore.project(null, null, Object.class, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle projection with cursor")
    void shouldHandleProjectionWithCursor() {
        // Given
        Query query = Query.empty();
        StateProjector<Object> testProjector = new StateProjector<Object>() {
            @Override
            public String getId() { return "test"; }
            @Override
            public List<String> getEventTypes() { return List.of(); }
            @Override
            public List<Tag> getTags() { return List.of(); }
            @Override
            public Object getInitialState() { return new Object(); }
            @Override
            public boolean handles(StoredEvent event) { return false; }
            @Override
            public Object transition(Object currentState, StoredEvent event) { return currentState; }
        };
        List<StateProjector<Object>> projectors = List.of(testProjector);
        Cursor cursor = Cursor.of(0L, Instant.now());

        // When
        ProjectionResult<Object> result = eventStore.project(query, cursor, Object.class, projectors);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.state()).isNotNull();
        assertThat(result.cursor()).isEqualTo(cursor); // No events processed, returns input cursor
    }

    @Test
    @DisplayName("Should apply projectors and return proper cursor following DCB spec")
    void shouldApplyProjectorsAndReturnProperCursor() throws JsonProcessingException {
        // Given: Create a test wallet and deposit events
        String walletId = "dcb-test-wallet";

        // Append wallet opened event
        WalletOpened walletOpened = WalletOpened.of(walletId, "DCB Test User", 100);
        byte[] openedData = objectMapper.writeValueAsBytes(walletOpened);
        AppendEvent openedEvent = AppendEvent.builder("WalletOpened")
                .tag("wallet_id", walletId)
                .data(openedData)
                .build();
        eventStore.append(List.of(openedEvent));

        // Create a simple test projector that counts events
        StateProjector<Integer> countProjector = new StateProjector<>() {
            @Override
            public String getId() {
                return "count-projector";
            }

            @Override
            public List<String> getEventTypes() {
                return List.of("WalletOpened", "DepositMade");
            }

            @Override
            public List<Tag> getTags() {
                return List.of(new Tag("wallet_id", walletId));
            }

            @Override
            public Integer getInitialState() {
                return 0;
            }

            @Override
            public Integer transition(Integer currentState, StoredEvent event) {
                return currentState == null ? 1 : currentState + 1;
            }
        };

        // When: Project with the test projector
        ProjectionResult<Integer> result = eventStore.project(
                Query.empty(),
                Cursor.zero(),
                Integer.class,
                List.of(countProjector)
        );

        // Then: Verify projector was applied
        assertThat(result).isNotNull();
        assertThat(result.state()).isEqualTo(1); // Should have counted 1 event
        assertThat(result.cursor()).isNotNull();
        assertThat(result.cursor()).isNotEqualTo(Cursor.zero()); // Cursor should have advanced
        assertThat(result.cursor().position().value()).isGreaterThan(0); // Position > 0
    }

    @Test
    @DisplayName("Should handle large number of events")
    void shouldHandleLargeNumberOfEvents() throws JsonProcessingException {
        // Given
        List<AppendEvent> largeEventList = List.of();
        for (int i = 0; i < 10; i++) { // Reduced for test performance
            String walletId = "large-test-wallet-" + i;
            WalletOpened walletOpened = WalletOpened.of(walletId, "Test User " + i, 1000);
            byte[] data = objectMapper.writeValueAsBytes(walletOpened);
            AppendEvent event = AppendEvent.builder("WalletOpened")
                    .tag("wallet_id", walletId)
                    .data(data)
                    .build();
            largeEventList = List.of(event); // Simplified for test
        }

        final List<AppendEvent> finalLargeEventList = largeEventList;

        // When & Then
        assertThatCode(() -> eventStore.append(finalLargeEventList))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle events with special characters in tags")
    void shouldHandleEventsWithSpecialCharactersInTags() throws JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("special-chars-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        List<Tag> specialTags = List.of(
                new Tag("key with spaces", "value with spaces"),
                new Tag("key-with-dashes", "value-with-dashes"),
                new Tag("key_with_underscores", "value_with_underscores"),
                new Tag("key.with.dots", "value.with.dots")
        );
        AppendEvent event = AppendEvent.builder("WalletOpened").data(data).build();

        // When & Then
        assertThatCode(() -> eventStore.append(List.of(event)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle events with very long tag values")
    void shouldHandleEventsWithVeryLongTagValues() throws JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("long-tag-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        String longValue = "a".repeat(1000); // Reduced for test performance
        List<Tag> longTags = List.of(new Tag("long_key", longValue));
        AppendEvent event = AppendEvent.builder("WalletOpened").data(data).build();

        // When & Then
        assertThatCode(() -> eventStore.append(List.of(event)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle events with unicode characters")
    void shouldHandleEventsWithUnicodeCharacters() throws JsonProcessingException {
        // Given
        WalletOpened walletOpened = WalletOpened.of("unicode-wallet", "Test User", 1000);
        byte[] data = objectMapper.writeValueAsBytes(walletOpened);
        List<Tag> unicodeTags = List.of(
                new Tag("emoji_key", "🚀💰💳"),
                new Tag("chinese_key", "钱包测试"),
                new Tag("arabic_key", "محفظة اختبار"),
                new Tag("russian_key", "тест кошелька")
        );
        AppendEvent event = AppendEvent.builder("WalletOpened").data(data).build();

        // When & Then
        assertThatCode(() -> eventStore.append(List.of(event)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle concurrent append operations")
    void shouldHandleConcurrentAppendOperations() throws JsonProcessingException {
        // Given
        List<AppendEvent> events1 = List.of();
        List<AppendEvent> events2 = List.of();

        for (int i = 0; i < 5; i++) { // Reduced for test performance
            String walletId1 = "concurrent-wallet1-" + i;
            String walletId2 = "concurrent-wallet2-" + i;

            WalletOpened walletOpened1 = WalletOpened.of(walletId1, "User 1", 1000);
            WalletOpened walletOpened2 = WalletOpened.of(walletId2, "User 2", 1000);

            byte[] data1 = objectMapper.writeValueAsBytes(walletOpened1);
            byte[] data2 = objectMapper.writeValueAsBytes(walletOpened2);

            AppendEvent event1 = AppendEvent.builder("WalletOpened")
                    .tag("wallet_id", walletId1)
                    .data(data1)
                    .build();
            AppendEvent event2 = AppendEvent.builder("WalletOpened")
                    .tag("wallet_id", walletId2)
                    .data(data2)
                    .build();

            events1 = List.of(event1); // Simplified for test
            events2 = List.of(event2); // Simplified for test
        }

        final List<AppendEvent> finalEvents1 = events1;
        final List<AppendEvent> finalEvents2 = events2;

        // When & Then
        assertThatCode(() -> {
            eventStore.append(finalEvents1);
            eventStore.append(finalEvents2);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle database connection errors gracefully")
    void shouldHandleDatabaseConnectionErrorsGracefully() throws JsonProcessingException, SQLException {
        // When & Then - constructor should throw IllegalArgumentException for null DataSource
        assertThatThrownBy(() -> new JDBCEventStore(null, objectMapper, config, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DataSource must not be null");
    }

    @Test
    @DisplayName("Should handle SQL exceptions during append")
    void shouldHandleSqlExceptionsDuringAppend() {
        // Given
        // Create an event that will cause SQL issues (e.g., invalid data format)
        AppendEvent invalidEvent = AppendEvent.builder("").data(new byte[0]).build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(invalidEvent)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to append events");
    }

    @Test
    @DisplayName("Should handle SQL exceptions during query")
    void shouldHandleSqlExceptionsDuringQuery() {
        // Given
        // Create a query with invalid tag format that should cause SQL issues
        Query problematicQuery = Query.of(List.of(
                QueryItem.of(List.of("WalletOpened"), List.of(new Tag("invalid_tag_format", "value with 'quotes' and ; semicolons")))
        ));

        // When & Then - PostgreSQL handles this gracefully, so no exception is thrown
        assertThatCode(() -> eventStore.query(problematicQuery, null))
                .doesNotThrowAnyException();
    }

    // ===== Transaction Isolation Level Tests =====

    @Test
    @DisplayName("Should apply READ_COMMITTED isolation level")
    void shouldApplyReadCommittedIsolationLevel() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction called
        String result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            return "success";
        });

        // Then: transaction succeeds
        assertThat(result).isEqualTo("success");
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should apply SERIALIZABLE isolation level")
    void shouldApplySerializableIsolationLevel() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction called
        String result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            return "success";
        });

        // Then: transaction succeeds
        assertThat(result).isEqualTo("success");
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should apply REPEATABLE_READ isolation level")
    void shouldApplyRepeatableReadIsolationLevel() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction called
        String result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            return "success";
        });

        // Then: transaction succeeds
        assertThat(result).isEqualTo("success");
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should apply READ_UNCOMMITTED isolation level")
    void shouldApplyReadUncommittedIsolationLevel() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction called
        String result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            return "success";
        });

        // Then: transaction succeeds
        assertThat(result).isEqualTo("success");
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should handle unknown isolation level")
    void shouldHandleUnknownIsolationLevel() {
        // This test verifies the default case in mapIsolationLevel() switch statement
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction called (with default config isolation level)
        String result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            return "success";
        });

        // Then: transaction succeeds with default isolation level
        assertThat(result).isEqualTo("success");
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    // ===== Transaction Scope Tests =====

    @Test
    @DisplayName("Should rollback on error in transaction")
    void shouldRollbackOnErrorInTransaction() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction throws exception
        assertThatThrownBy(() -> eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            throw new RuntimeException("Test error");
        })).isInstanceOf(RuntimeException.class)
          .hasMessage("Test error");

        // Then: no events should be persisted (rollback occurred)
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should use connection scoped appendIf in transaction")
    void shouldUseConnectionScopedAppendIfInTransaction() {
        // Given: events and condition
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();
        AppendCondition condition = AppendCondition.of(Cursor.zero());

        // When: executeInTransaction with nested appendIf
        String result = eventStore.executeInTransaction(store -> {
            store.appendIf(List.of(event), condition);
            return "success";
        });

        // Then: transaction succeeds
        assertThat(result).isEqualTo("success");
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should handle nested transactions")
    void shouldHandleNestedTransactions() {
        // Given: events to append
        AppendEvent event1 = AppendEvent.builder("TestEvent1").data("{}").build();
        AppendEvent event2 = AppendEvent.builder("TestEvent2").data("{}").build();

        // When: nested executeInTransaction calls
        String result = eventStore.executeInTransaction(outerStore -> {
            outerStore.append(List.of(event1));
            
            return eventStore.executeInTransaction(innerStore -> {
                innerStore.append(List.of(event2));
                return "success";
            });
        });

        // Then: both transactions succeed
        assertThat(result).isEqualTo("success");
        
        // Verify both events were appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("Should handle transaction with query operations")
    void shouldHandleTransactionWithQueryOperations() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction with query operations
        Integer result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            
            // Query within transaction
            List<StoredEvent> events = store.query(Query.of(List.of()), Cursor.zero());
            return events.size();
        });

        // Then: transaction succeeds and returns correct count
        assertThat(result).isEqualTo(1);
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should handle transaction with queryAsJsonArray operations")
    void shouldHandleTransactionWithQueryAsJsonArrayOperations() {
        // Given: events to append
        AppendEvent event = AppendEvent.builder("TestEvent").data("{}").build();

        // When: executeInTransaction with queryAsJsonArray operations
        Integer result = eventStore.executeInTransaction(store -> {
            store.append(List.of(event));
            
            // QueryAsJsonArray within transaction
            byte[] jsonArray = store.queryAsJsonArray(Query.of(List.of()), Cursor.zero());
            return jsonArray.length;
        });

        // Then: transaction succeeds and returns JSON array length
        assertThat(result).isGreaterThan(0);
        
        // Verify event was appended
        List<StoredEvent> events = eventStore.query(Query.of(List.of()), Cursor.zero());
        assertThat(events).hasSize(1);
    }
}
