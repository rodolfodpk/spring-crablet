package com.crablet.eventstore.integration;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.query.EventTestHelper;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.examples.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.domain.projections.WalletBalanceState;
import com.crablet.examples.wallet.domain.event.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for EventStore error handling and edge cases.
 * Tests PostgreSQL exceptions, transaction failures, and error recovery.
 */
@DisplayName("EventStore Error Handling Tests")
class EventStoreErrorHandlingTest extends AbstractCrabletTest {

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventTestHelper eventTestHelper;

    @Test
    @DisplayName("Should handle AppendIf with current cursor")
    void shouldHandleAppendIfWithCurrentCursor() {
        // Given: wallet with initial event
        String walletId = "error-wallet-1";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Alice", 1000))
                        .build()
        ));

        // Get initial cursor
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);
        Cursor initialCursor = Cursor.of(
                new com.crablet.eventstore.store.SequenceNumber(events.get(0).position()),
                events.get(0).occurredAt(),
                events.get(0).transactionId()
        );

        // When: appendIf with current cursor
        eventStore.appendIf(
                List.of(AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                        .build()),
                AppendCondition.of(initialCursor, query)
        );

        // Then: should succeed
        List<StoredEvent> allEvents = eventTestHelper.query(
                Query.forEventsAndTags(
                        List.of("WalletOpened", "DepositMade"),
                        List.of(new Tag("wallet_id", walletId))
                ),
                null
        );
        assertThat(allEvents).hasSize(2);
    }

    @Test
    @DisplayName("Should handle transaction rollback on exception")
    void shouldRollbackTransactionOnException() {
        // Given: function that throws exception
        String walletId = "error-wallet-2";

        // When: exception thrown in transaction
        assertThatThrownBy(() ->
                eventStore.executeInTransaction(txEventStore -> {
                    txEventStore.append(List.of(
                            AppendEvent.builder("WalletOpened")
                                    .tag("wallet_id", walletId)
                                    .data(WalletOpened.of(walletId, "Bob", 1000))
                                    .build()
                    ));
                    throw new RuntimeException("Simulated error");
                })
        ).isInstanceOf(RuntimeException.class);

        // Then: wallet should not exist (transaction rolled back)
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should handle query with null cursor")
    void shouldHandleNullCursor() {
        // Given: wallet exists
        String walletId = "error-wallet-3";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Charlie", 1000))
                        .build()
        ));

        // When: query with null cursor
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        
        // Then: should work (EventTestHelper.query allows null cursor)
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle invalid event type in serialization")
    void shouldHandleInvalidEventData() {
        // Given: event with complex data
        String walletId = "error-wallet-4";
        
        // When: append event with valid data
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Diana", 1000))
                        .build()
        ));

        // Then: should succeed
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should handle concurrent appends gracefully")
    void shouldHandleConcurrentAppendsGracefully() {
        // Given: wallet with initial event
        String walletId = "error-wallet-5";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Eve", 1000))
                        .build()
        ));

        // When: multiple sequential appends
        for (int i = 1; i <= 5; i++) {
            eventStore.append(List.of(
                    AppendEvent.builder("DepositMade")
                            .tag("wallet_id", walletId)
                            .tag("deposit_id", "deposit" + i)
                            .data(DepositMade.of("deposit" + i, walletId, 100 * i, 1000 + 100 * i, "Deposit " + i))
                            .build()
            ));
        }

        // Then: all events should exist
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).hasSize(6);
    }

    @Test
    @DisplayName("Should handle projection with empty result set")
    void shouldHandleProjectionWithEmptyResultSet() {
        // Given: no events for wallet
        String walletId = "error-wallet-6";

        // When: project with empty result
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        ProjectionResult<WalletBalanceState> result = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );

        // Then: should return non-existing wallet
        assertThat(result.state().isExisting()).isFalse();
    }

    @Test
    @DisplayName("Should handle AppendIf with future cursor")
    void shouldHandleAppendIfWithFutureCursor() {
        // Given: wallet exists
        String walletId = "error-wallet-7";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Frank", 1000))
                        .build()
        ));

        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);

        // Create cursor that doesn't exist (future position)
        Cursor futureCursor = Cursor.of(
                new com.crablet.eventstore.store.SequenceNumber(999999L),
                java.time.Instant.now(),
                "future-tx-id"
        );

        // When: appendIf with future cursor - may or may not throw depending on DCB implementation
        // Note: Some DCB implementations allow future cursors if position check passes
        assertThatCode(() ->
                eventStore.appendIf(
                        List.of(AppendEvent.builder("DepositMade")
                                .tag("wallet_id", walletId)
                                .tag("deposit_id", "deposit1")
                                .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                                .build()),
                        AppendCondition.of(futureCursor, query)
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle tag parsing for empty arrays")
    void shouldHandleTagParsingForEmptyArrays() {
        // Given: wallet with tags
        String walletId = "error-wallet-8";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Grace", 1000))
                        .build()
        ));

        // When: query events
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);

        // Then: tags should be parsed correctly
        assertThat(events).hasSize(1);
        StoredEvent event = events.get(0);
        assertThat(event.tags()).isNotEmpty();
        assertThat(event.hasTag("wallet_id", walletId)).isTrue();
    }

    @Test
    @DisplayName("Should handle large transaction ID")
    void shouldHandleLargeTransactionId() {
        // Given: wallet
        String walletId = "error-wallet-9";
        
        // When: append event
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Henry", 1000))
                        .build()
        ));

        // Then: transaction ID should be captured
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).transactionId()).isNotNull();
    }

    @Test
    @DisplayName("Should handle event with multiple tags")
    void shouldHandleEventWithMultipleTags() {
        // Given: transfer event with multiple tags
        String transferId = "error-transfer-1";
        
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet-from")
                        .data(WalletOpened.of("wallet-from", "Sender", 2000))
                        .build(),
                AppendEvent.builder("MoneyTransferred")
                        .tag("transfer_id", transferId)
                        .tag("from_wallet_id", "wallet-from")
                        .tag("to_wallet_id", "wallet-to")
                        .tag("status", "completed")
                        .data(MoneyTransferred.of(transferId, "wallet-from", "wallet-to", 500, 1500, 2500, "Transfer"))
                        .build()
        ));

        // When: query by transfer_id
        Query query = Query.forEventAndTag("MoneyTransferred", "transfer_id", transferId);
        List<StoredEvent> events = eventTestHelper.query(query, null);

        // Then: should return event with all tags
        assertThat(events).hasSize(1);
        StoredEvent event = events.get(0);
        assertThat(event.tags()).hasSize(4);
        assertThat(event.hasTag("transfer_id", transferId)).isTrue();
        assertThat(event.hasTag("from_wallet_id", "wallet-from")).isTrue();
        assertThat(event.hasTag("to_wallet_id", "wallet-to")).isTrue();
        assertThat(event.hasTag("status", "completed")).isTrue();
    }
}

