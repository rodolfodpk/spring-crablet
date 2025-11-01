package com.crablet.eventstore.integration;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.EventTestHelper;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.SequenceNumber;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.examples.wallet.domain.event.*;
import com.crablet.examples.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.domain.projections.WalletBalanceState;
import com.crablet.examples.wallet.features.deposit.DepositCommand;
import com.crablet.examples.wallet.features.openwallet.OpenWalletCommand;
import com.crablet.examples.wallet.features.transfer.TransferMoneyCommand;
import com.crablet.examples.wallet.features.withdraw.WithdrawCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for EventStore core API using Wallet domain.
 * Tests direct event store operations with real PostgreSQL and realistic wallet scenarios.
 */
@DisplayName("EventStore Integration Tests")
class EventStoreTest extends AbstractCrabletTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventTestHelper eventTestHelper;

    @Test
    @DisplayName("Should append wallet events without conditions")
    void shouldAppendEventsWithoutConditions() {
        // Given: wallet lifecycle events
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        DepositMade depositMade = DepositMade.of("deposit1", "wallet1", 500, 1500, "Salary");

        List<AppendEvent> events = List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", "wallet1")
                        .data(walletOpened)
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", "wallet1")
                        .tag("deposit_id", "deposit1")
                        .data(depositMade)
                        .build()
        );

        // When
        eventStore.append(events);

        // Then: verify events were stored
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "wallet1");
        List<StoredEvent> storedEvents = eventTestHelper.query(query, null);
        
        assertThat(storedEvents).hasSize(1);
        assertThat(storedEvents.get(0).type()).isEqualTo("WalletOpened");
        assertThat(storedEvents.get(0).hasTag("wallet_id", "wallet1")).isTrue();
    }

    @Test
    @DisplayName("Should appendIf with valid cursor condition (DCB pattern)")
    void shouldAppendIfWithValidCondition() {
        // Given: wallet with initial balance
        String walletId = "wallet2";
        WalletOpened walletOpened = WalletOpened.of(walletId, "Bob", 1000);

        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(walletOpened)
                        .build()
        ));

        // Project to get current cursor (DCB pattern)
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        ProjectionResult<WalletBalanceState> result = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );

        // When: deposit with valid cursor condition
        DepositMade deposit = DepositMade.of("deposit1", walletId, 500, 1500, "Bonus");
        AppendCondition condition = AppendCondition.of(result.cursor(), query);

        eventStore.appendIf(
                List.of(AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(deposit)
                        .build()),
                condition
        );

        // Then: both events should exist
        Query allWalletEventsQuery = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> allEvents = eventTestHelper.query(allWalletEventsQuery, null);
        assertThat(allEvents).hasSize(2);
    }

    @Test
    @DisplayName("Should throw ConcurrencyException when appendIf with stale cursor (DCB scenario)")
    void shouldThrowConcurrencyExceptionWhenAppendIfWithStaleCursor() {
        // Given: wallet with initial deposit
        String walletId = "wallet3";
        WalletOpened walletOpened = WalletOpened.of(walletId, "Charlie", 1000);

        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(walletOpened)
                        .build()
        ));

        // Project to get initial cursor
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        ProjectionResult<WalletBalanceState> result1 = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );

        // Simulate concurrent modification: another deposit happens
        DepositMade concurrentDeposit = DepositMade.of("deposit1", walletId, 500, 1500, "Concurrent deposit");
        eventStore.append(List.of(
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(concurrentDeposit)
                        .build()
        ));

        // When/Then: try to appendIf with stale cursor (DCB concurrency control)
        WithdrawalMade staleWithdrawal = WithdrawalMade.of("withdrawal1", walletId, 200, 800, "Stale withdrawal");
        AppendCondition staleCondition = AppendCondition.of(result1.cursor(), query);

        assertThatThrownBy(() ->
                eventStore.appendIf(
                        List.of(AppendEvent.builder("WithdrawalMade")
                                .tag("wallet_id", walletId)
                                .tag("withdrawal_id", "withdrawal1")
                                .data(staleWithdrawal)
                                .build()),
                        staleCondition
                )
        )
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("AppendCondition violated");
    }

    @Test
    @DisplayName("Should project wallet balance with event type filters")
    void shouldProjectStateWithFilters() {
        // Given: wallet with deposits and withdrawals
        String walletId = "wallet4";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Diana", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit 1"))
                        .build(),
                AppendEvent.builder("WithdrawalMade")
                        .tag("wallet_id", walletId)
                        .tag("withdrawal_id", "withdrawal1")
                        .data(WithdrawalMade.of("withdrawal1", walletId, 300, 1200, "Withdrawal 1"))
                        .build()
        ));

        // When: project with all wallet event types
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", walletId))
        );

        ProjectionResult<WalletBalanceState> result = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );

        // Then: should project final balance correctly
        WalletBalanceState state = result.state();
        assertThat(state.isExisting()).isTrue();
        assertThat(state.balance()).isEqualTo(1200); // 1000 + 500 - 300
        assertThat(state.walletId()).isEqualTo(walletId);
    }

    @Test
    @DisplayName("Should execute wallet lifecycle in transaction with commit")
    void shouldExecuteInTransactionWithCommit() {
        // Given: wallet lifecycle scenario
        String walletId = "wallet6";

        // When: execute wallet opening and deposit in transaction
        String txId = eventStore.executeInTransaction(txEventStore -> {
            txEventStore.append(List.of(
                    AppendEvent.builder("WalletOpened")
                            .tag("wallet_id", walletId)
                            .data(WalletOpened.of(walletId, "Eve", 1000))
                            .build(),
                    AppendEvent.builder("DepositMade")
                            .tag("wallet_id", walletId)
                            .tag("deposit_id", "deposit1")
                            .data(DepositMade.of("deposit1", walletId, 500, 1500, "Initial deposit"))
                            .build()
            ));
            return txEventStore.getCurrentTransactionId();
        });

        // Then: both events should be persisted atomically
        assertThat(txId).isNotNull();
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("Should rollback wallet transaction on exception")
    void shouldRollbackTransactionOnException() {
        // Given: wallet operation scenario
        String walletId = "wallet7";

        // When: transaction throws exception after wallet creation
        assertThatThrownBy(() ->
                eventStore.executeInTransaction(txEventStore -> {
                    txEventStore.append(List.of(
                            AppendEvent.builder("WalletOpened")
                                    .tag("wallet_id", walletId)
                                    .data(WalletOpened.of(walletId, "Frank", 1000))
                                    .build()
                    ));
                    throw new RuntimeException("Simulated transaction error");
                })
        ).isInstanceOf(RuntimeException.class);

        // Then: wallet should not be persisted (rollback successful)
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should store wallet command metadata")
    void shouldStoreCommandMetadata() {
        // Given: wallet command
        OpenWalletCommand openCmd = OpenWalletCommand.of("wallet8", "Grace", 1000);

        // When: store command
        String txId = eventStore.executeInTransaction(txEventStore -> {
            String transactionId = txEventStore.getCurrentTransactionId();
            txEventStore.storeCommand(openCmd, transactionId);
            return transactionId;
        });

        // Then: verify command stored
        Integer openCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commands WHERE type = ?",
                Integer.class,
                "open_wallet"
        );
        assertThat(openCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should paginate wallet events with cursor")
    void shouldPaginateWithCursor() {
        // Given: wallet with 5 deposits
        String walletId = "wallet9";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Henry", 1000))
                        .build()
        ));
        
        int runningBalance = 1000;
        for (int i = 1; i <= 5; i++) {
            runningBalance += i * 100;
            eventStore.append(List.of(
                    AppendEvent.builder("DepositMade")
                            .tag("wallet_id", walletId)
                            .tag("deposit_id", "deposit" + i)
                            .data(DepositMade.of("deposit" + i, walletId, i * 100, runningBalance, "Deposit " + i))
                            .build()
            ));
        }

        // When: project all events
        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        ProjectionResult<WalletBalanceState> result1 = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );
        
        Cursor cursor1 = result1.cursor();
        assertThat(result1.state().balance()).isEqualTo(2500); // 1000 + (100 + 200 + 300 + 400 + 500)

        // Then: project again using cursor (no new events)
        ProjectionResult<WalletBalanceState> result2 = eventStore.project(
                query,
                cursor1,
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );

        // Verify cursor stays at same position with no new events
        // Note: when projecting from cursor with no new events, state starts fresh (initial state)
        assertThat(result2.cursor()).isEqualTo(cursor1);
        assertThat(result2.state().isExisting()).isFalse(); // No events processed since cursor
    }

    @Test
    @DisplayName("Should deserialize complex wallet transfer event data")
    void shouldDeserializeComplexEventData() {
        // Given: complex transfer event with multiple wallet IDs and balances
        String transferId = "transfer1";
        MoneyTransferred transfer = MoneyTransferred.of(
                transferId,
                "wallet10",
                "wallet11",
                500,
                1500, // from balance
                2500, // to balance
                "Complex transfer test"
        );

        eventStore.append(List.of(
                AppendEvent.builder("MoneyTransferred")
                        .tag("transfer_id", transferId)
                        .tag("from_wallet_id", "wallet10")
                        .tag("to_wallet_id", "wallet11")
                        .data(transfer)
                        .build()
        ));

        // When: query and deserialize
        Query query = Query.forEventAndTag("MoneyTransferred", "transfer_id", transferId);
        List<StoredEvent> events = eventTestHelper.query(query, null);

        // Then: verify complex event structure is preserved
        assertThat(events).hasSize(1);
        StoredEvent stored = events.get(0);
        
        // Deserialize using EventDeserializer pattern
        EventDeserializer deserializer = new EventDeserializer() {
            @Override
            public <E> E deserialize(StoredEvent event, Class<E> eventType) {
                try {
                    return objectMapper.readValue(event.data(), eventType);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        
        MoneyTransferred deserialized = deserializer.deserialize(stored, MoneyTransferred.class);
        assertThat(deserialized.transferId()).isEqualTo(transferId);
        assertThat(deserialized.fromWalletId()).isEqualTo("wallet10");
        assertThat(deserialized.toWalletId()).isEqualTo("wallet11");
        assertThat(deserialized.amount()).isEqualTo(500);
        assertThat(deserialized.fromBalance()).isEqualTo(1500);
        assertThat(deserialized.toBalance()).isEqualTo(2500);
    }


    @Test
    @DisplayName("Should handle multiple wallet event types in a single query")
    void shouldHandleMultipleQueryItems() {
        // Given: wallet with different event types
        String walletId = "wallet12";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Ivy", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                        .build()
        ));

        // When: query with multiple event types
        Query query = Query.of(List.of(
                QueryItem.of(List.of("WalletOpened"), List.of(new Tag("wallet_id", walletId))),
                QueryItem.of(List.of("DepositMade"), List.of(new Tag("wallet_id", walletId)))
        ));

        List<StoredEvent> events = eventTestHelper.query(query, null);

        // Then: should find both event types
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("Should handle empty query to match all wallet events")
    void shouldHandleEmptyQuery() {
        // Given: wallet events
        String walletId = "wallet13";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Jack", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit"))
                        .build()
        ));

        // When: query with empty query
        Query query = Query.empty();
        List<StoredEvent> events = eventTestHelper.query(query, null);

        // Then: should return all events (including our wallet events)
        assertThat(events.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should handle cursor zero for initial wallet projection")
    void shouldHandleCursorZero() {
        // Given: new wallet
        String walletId = "wallet14";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Kate", 1000))
                        .build()
        ));

        // When: project with cursor zero (initial projection)
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        ProjectionResult<WalletBalanceState> result = eventStore.project(
                query,
                Cursor.zero(),
                WalletBalanceState.class,
                List.of(new WalletBalanceProjector())
        );

        // Then: should project successfully from beginning
        assertThat(result.cursor()).isNotNull();
        assertThat(result.state().isExisting()).isTrue();
        assertThat(result.state().balance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should handle appendIf with empty query for wallet")
    void shouldAppendIfWithEmptyQuery() {
        // Given: empty query condition
        String walletId = "wallet15";

        // When: appendIf with empty query (allows duplicate appends)
        AppendCondition condition = AppendCondition.of(Cursor.zero(), Query.empty());
        
        eventStore.appendIf(
                List.of(AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Leo", 1000))
                        .build()),
                condition
        );

        // Then: should append successfully
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", walletId);
        List<StoredEvent> events = eventTestHelper.query(query, null);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Should handle deposit idempotency check with operation_id")
    void shouldHandleIdempotencyCheck() {
        // Given: wallet with initial deposit
        String walletId = "wallet16";
        String depositId = "deposit1";
        
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Mia", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", depositId)
                        .data(DepositMade.of(depositId, walletId, 500, 1500, "Initial deposit"))
                        .build()
        ));

        // When: try to append same deposit again with idempotency check
        Query idempotencyQuery = Query.forEventAndTag("DepositMade", "deposit_id", depositId);
        AppendCondition idempotencyCondition = new AppendCondition(
                Cursor.zero(),  // cursor doesn't matter for idempotency
                Query.empty(),  // state change query (not checking)
                idempotencyQuery  // already exists check
        );

        // Then: should fail because deposit already exists
        assertThatThrownBy(() ->
                eventStore.appendIf(
                        List.of(AppendEvent.builder("DepositMade")
                                .tag("wallet_id", walletId)
                                .tag("deposit_id", depositId)
                                .data(DepositMade.of(depositId, walletId, 500, 1500, "Duplicate deposit"))
                                .build()),
                        idempotencyCondition
                )
        ).isInstanceOf(ConcurrencyException.class);
    }

    @Test
    @DisplayName("Should handle wallet transfer with complex multi-tag structure")
    void shouldHandleComplexTags() {
        // Given: transfer event with multiple tags
        String transferId = "transfer2";
        List<AppendEvent> events = List.of(
                AppendEvent.builder("MoneyTransferred")
                        .tag("transfer_id", transferId)
                        .tag("from_wallet_id", "wallet17")
                        .tag("to_wallet_id", "wallet18")
                        .tag("status", "completed")
                        .data(MoneyTransferred.of(
                                transferId,
                                "wallet17",
                                "wallet18",
                                300,
                                700,
                                1300,
                                "Multi-tag transfer"
                        ))
                        .build()
        );

        // When
        eventStore.append(events);

        // Then: verify all tags were stored
        Query query = Query.forEventAndTag("MoneyTransferred", "transfer_id", transferId);
        List<StoredEvent> stored = eventTestHelper.query(query, null);
        
        assertThat(stored).hasSize(1);
        StoredEvent event = stored.get(0);
        assertThat(event.tags()).hasSize(4);
        assertThat(event.hasTag("transfer_id", transferId)).isTrue();
        assertThat(event.hasTag("from_wallet_id", "wallet17")).isTrue();
        assertThat(event.hasTag("to_wallet_id", "wallet18")).isTrue();
        assertThat(event.hasTag("status", "completed")).isTrue();
    }

    @Test
    @DisplayName("Should handle wallet projection with WalletBalanceProjector")
    void shouldProjectWithMultipleProjectors() {
        // Given: wallet with deposits
        String walletId = "wallet19";
        eventStore.append(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Nina", 1000))
                        .build(),
                AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "deposit1")
                        .data(DepositMade.of("deposit1", walletId, 500, 1500, "Deposit 1"))
                        .build()
        ));

        // When: project with WalletBalanceProjector
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

        // Then: should project balance correctly
        WalletBalanceState state = result.state();
        assertThat(state.isExisting()).isTrue();
        assertThat(state.balance()).isEqualTo(1500);
    }
}

