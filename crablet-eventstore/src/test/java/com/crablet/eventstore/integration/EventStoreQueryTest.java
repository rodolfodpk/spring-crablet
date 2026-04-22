package com.crablet.eventstore.integration;

import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import com.crablet.examples.wallet.projections.WalletBalanceState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventStore query operations.
 * Tests querying with stream positions, filtering, pagination, and edge cases.
 */
@DisplayName("EventStore Query Tests")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class EventStoreQueryTest extends com.crablet.test.AbstractCrabletTest {

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventRepository eventRepository;

    @Test
    @DisplayName("Should query with stream position pagination")
    void shouldQueryWithStreamPositionPagination() {
        // Given: wallet with multiple deposits
        String walletId = "query-wallet-1";
        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Alice", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "First deposit"))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit2")
                        .data(DepositMade.of("deposit2", walletId, 300, 1800, "Second deposit"))
                        .build()
        );
        eventStore.appendCommutative(events);

        // When: query first page
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> page1 = eventRepository.query(query, null);

        assertThat(page1).hasSize(3);

        // Get stream position from first event
        StreamPosition afterFirst = StreamPosition.of(
                page1.get(1).position(),
                page1.get(1).occurredAt(),
                page1.get(1).transactionId()
        );

        // Query second page from stream position
        List<StoredEvent> page2 = eventRepository.query(query, afterFirst);

        // Then: should return remaining events
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).type()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should query with type and tag filtering")
    void shouldQueryWithTypeAndTagFiltering() {
        // Given: wallet with different event types
        String walletId = "query-wallet-2";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Bob", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                        .build(),
                AppendEvent.builder("WithdrawalMade")
                        .tag("wallet_id", walletId)
                        .tag("withdrawal_id", "withdrawal1")
                        .data(WithdrawalMade.of("withdrawal1", walletId, 200, 1300, "Withdrawal"))
                        .build()
        ));

        // When: query only deposit events
        Query depositQuery = Query.forEventAndTag("DepositMade", "wallet_id", walletId);
        List<StoredEvent> deposits = eventRepository.query(depositQuery, null);

        // Then: should only return deposits
        assertThat(deposits).hasSize(1);
        assertThat(deposits.get(0).type()).isEqualTo("DepositMade");
    }

    @Test
    @DisplayName("Should return empty results when no matching events")
    void shouldReturnEmptyResultsWhenNoMatchingEvents() {
        // Given: wallet exists
        String walletId = "query-wallet-3";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Charlie", 1000))
                        .build()
        ));

        // When: query for non-existent event type
        Query query = Query.forEventAndTag("DepositMade", "wallet_id", walletId);
        List<StoredEvent> results = eventRepository.query(query, null);

        // Then: should return empty list
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle invalid stream position gracefully")
    void shouldHandleInvalidStreamPositionGracefully() {
        // Given: wallet with one event
        String walletId = "query-wallet-4";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Diana", 1000))
                        .build()
        ));

        // When: query with stream position beyond existing events
        StreamPosition futureStreamPosition = StreamPosition.of(
                999999L,
                java.time.Instant.now(),
                "future-tx-id"
        );
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> results = eventRepository.query(query, futureStreamPosition);

        // Then: should return empty list (no events after that stream position)
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle large result sets with pagination")
    void shouldHandleLargeResultSetsWithPagination() {
        // Given: wallet with many events
        String walletId = "query-wallet-5";
        List<AppendEvent> events = new java.util.ArrayList<>();
        events.add(AppendEvent.builder("WalletOpened")
                .tag("wallet_id", walletId)
                .data(WalletOpened.of(walletId, "Eve", 1000))
                .build());

        for (int i = 1; i <= 50; i++) {
            events.add(AppendEvent.builder("DepositMade")
                    .tag("wallet_id", walletId)
                    .tag("deposit_id", "deposit" + i)
                    .data(DepositMade.of("deposit" + i, walletId, 100, 1000 + i * 100, "Deposit " + i))
                    .build());
        }
        eventStore.appendCommutative(events);

        // When: query with stream position pagination
        Query query = Query.forEventsAndTags(
                List.of("DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> allResults = eventRepository.query(query, null);

        // Then: should return all deposit events
        assertThat(allResults).hasSize(50);

        // Verify streamPosition-based pagination works
        StreamPosition afterMidpoint = StreamPosition.of(
                allResults.get(25).position(),
                allResults.get(25).occurredAt(),
                allResults.get(25).transactionId()
        );
        List<StoredEvent> secondHalf = eventRepository.query(query, afterMidpoint);

        assertThat(secondHalf).hasSize(24); // 50 total - 25 after stream position = 24 remaining
    }

    @Test
    @DisplayName("Should query with multiple tag filters")
    void shouldQueryWithMultipleTagFilters() {
        // Given: transfer event with multiple tags
        String transferId = "transfer-1";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet-from")
                        .data(WalletOpened.of("wallet-from", "Sender", 2000))
                        .build(),
                AppendEvent.builder("MoneyTransferred")
                        .tag("transfer_id", transferId)
                        .tag("from_wallet_id", "wallet-from")
                        .tag("to_wallet_id", "wallet-to")
                        .data(MoneyTransferred.of(transferId, "wallet-from", "wallet-to", 500, 1500, 2500, "Transfer"))
                        .build()
        ));

        // When: query transfer by transfer_id
        Query query = Query.forEventAndTag("MoneyTransferred", "transfer_id", transferId);
        List<StoredEvent> results = eventRepository.query(query, null);

        // Then: should return transfer event with all tags
        assertThat(results).hasSize(1);
        StoredEvent transfer = results.get(0);
        assertThat(transfer.hasTag("transfer_id", transferId)).isTrue();
        assertThat(transfer.hasTag("from_wallet_id", "wallet-from")).isTrue();
        assertThat(transfer.hasTag("to_wallet_id", "wallet-to")).isTrue();
    }

    @Test
    @DisplayName("Should handle query with empty query items")
    void shouldHandleQueryWithEmptyQueryItems() {
        // Given: events exist
        String walletId = "query-wallet-6";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Frank", 1000))
                        .build()
        ));

        // When: query with empty items (should match all events)
        Query emptyQuery = Query.empty();
        List<StoredEvent> allEvents = eventRepository.query(emptyQuery, null);

        // Then: should return events (at least our wallet event)
        assertThat(allEvents.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should project with query and stream position")
    void shouldProjectWithQueryAndStreamPosition() {
        // Given: wallet with events
        String walletId = "query-wallet-7";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Grace", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                        .build()
        ));

        // When: project with wallet balance state projector
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        ProjectionResult<WalletBalanceState> result = eventStore.project(
                query,
                StreamPosition.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceStateProjector())
        );

        // Then: should project correct balance
        assertThat(result.state().isExisting()).isTrue();
        assertThat(result.state().balance()).isEqualTo(1500);
        assertThat(result.streamPosition()).isNotNull();
    }

    @Test
    @DisplayName("Should query with multiple query items")
    void shouldQueryWithMultipleQueryItems() {
        // Given: events with different tags
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet-1")
                        .data(WalletOpened.of("wallet-1", "Alice", 1000))
                        .build(),
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet-2")
                        .data(WalletOpened.of("wallet-2", "Bob", 2000))
                        .build()
        ));

        // When: query with multiple query items (OR logic)
        Query query = Query.of(List.of(
                QueryItem.of(List.of("WalletOpened"), List.of(new Tag("wallet_id", "wallet-1"))),
                QueryItem.of(List.of("WalletOpened"), List.of(new Tag("wallet_id", "wallet-2")))
        ));
        List<StoredEvent> results = eventRepository.query(query, null);

        // Then: should return both wallet events
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should handle stream position at exact position")
    void shouldHandleStreamPositionAtExactPosition() {
        // Given: wallet with multiple events
        String walletId = "query-wallet-8";
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Henry", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit2")
                        .data(DepositMade.of("deposit2", walletId, 300, 1800, "Deposit"))
                        .build()
        ));

        // When: query all events
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> allEvents = eventRepository.query(query, null);
        assertThat(allEvents).hasSize(3);

        // Use stream position at second event position
        StreamPosition streamPositionAtSecond = StreamPosition.of(
                allEvents.get(1).position(),
                allEvents.get(1).occurredAt(),
                allEvents.get(1).transactionId()
        );

        // Query from that stream position
        List<StoredEvent> afterSecond = eventRepository.query(query, streamPositionAtSecond);

        // Then: should return events after that position
        assertThat(afterSecond).hasSize(1);
        long eventPosition = afterSecond.get(0).position();
        long streamPositionValue = streamPositionAtSecond.position();
        assertThat(eventPosition).isGreaterThan(streamPositionValue);
    }
}
