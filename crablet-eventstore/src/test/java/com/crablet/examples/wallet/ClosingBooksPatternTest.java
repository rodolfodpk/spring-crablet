package com.crablet.examples.wallet;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.integration.AbstractCrabletTest;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.projections.WalletBalanceState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for Closing the Books pattern with wallet statements.
 * 
 * Scenario:
 * - January 2024: Wallet opened, deposits, withdrawals, transfers
 * - End of January: Statement closed
 * - February 2024: Statement opened, new deposits, withdrawals
 * 
 * Verifies that:
 * - Querying February only returns February events (not January)
 * - WalletStatementOpened sets opening balance
 * - Subsequent events update balance correctly
 * - Events are processed sequentially (WalletStatementOpened first, then others)
 */
@DisplayName("Closing the Books Pattern Test")
class ClosingBooksPatternTest extends AbstractCrabletTest {

    @Autowired
    private EventStore eventStore;
    
    @Autowired
    private EventRepository eventRepository;
    
    @Autowired
    private com.crablet.eventstore.clock.ClockProvider clockProvider;
    
    /**
     * Query for wallet events in a specific period.
     * Only returns events with matching period tags (year, month).
     */
    private Query walletPeriodQuery(String walletId, int year, int month) {
        return WalletQueryPatterns.singleWalletPeriodDecisionModel(walletId, year, month);
    }
    
    @Test
    @DisplayName("Should only query events from current period, not closed periods")
    void shouldOnlyQueryCurrentPeriodEvents() {
        String walletId = "wallet-closing-books-1";
        
        // ===== JANUARY 2024 =====
        // January: Open wallet and make transactions
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", walletId)
                .tag("year", "2024")
                .tag("month", "1")
                .data(WalletOpened.of(walletId, "Alice", 1000))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", walletId)
                .tag("deposit_id", "dep1")
                .tag("year", "2024")
                .tag("month", "1")
                .data(DepositMade.of("dep1", walletId, 500, 1500, "Jan deposit"))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", walletId)
                .tag("withdrawal_id", "w1")
                .tag("year", "2024")
                .tag("month", "1")
                .data(WithdrawalMade.of("w1", walletId, 200, 1300, "Jan withdrawal"))
                .build()
        ), AppendCondition.empty());
        
        // Close January statement
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletStatementClosed")
                .tag("wallet_id", walletId)
                .tag("statement_id", "wallet:" + walletId + ":2024-01")
                .tag("year", "2024")
                .tag("month", "1")
                .data(WalletStatementClosed.of(
                    walletId,
                    "wallet:" + walletId + ":2024-01",
                    2024,
                    1,   // month
                    null, // day
                    null, // hour
                    1000,  // opening
                    1300   // closing
                ))
                .build()
        ), AppendCondition.empty());
        
        // ===== FEBRUARY 2024 =====
        // February: Open new statement with opening balance from January
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletStatementOpened")
                .tag("wallet_id", walletId)
                .tag("statement_id", "wallet:" + walletId + ":2024-02")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WalletStatementOpened.of(
                    walletId,
                    "wallet:" + walletId + ":2024-02",
                    2024,
                    2,    // month
                    null, // day
                    null, // hour
                    1300  // Opening balance from January closing
                ))
                .build()
        ), AppendCondition.empty());
        
        // February: New transactions
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", walletId)
                .tag("deposit_id", "dep2")
                .tag("year", "2024")
                .tag("month", "2")
                .data(DepositMade.of("dep2", walletId, 300, 1600, "Feb deposit"))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", walletId)
                .tag("withdrawal_id", "w2")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WithdrawalMade.of("w2", walletId, 100, 1500, "Feb withdrawal"))
                .build()
        ), AppendCondition.empty());
        
        // ===== VERIFY =====
        // Query February events only
        Query febQuery = walletPeriodQuery(walletId, 2024, 2);
        List<StoredEvent> febEvents = eventRepository.query(febQuery, null);
        
        // Should only have February events (period query includes WalletOpened for wallet existence)
        assertThat(febEvents).hasSize(4); // WalletOpened, StatementOpened, DepositMade, WithdrawalMade
        assertThat(febEvents).extracting(StoredEvent::type)
            .containsExactly("WalletOpened", "WalletStatementOpened", "DepositMade", "WithdrawalMade");
        
        // Verify no January events (except WalletOpened which is included for wallet existence)
        assertThat(febEvents).noneMatch(e -> {
            // WalletOpened is included for wallet existence, so skip it
            if ("WalletOpened".equals(e.type())) {
                return false;
            }
            boolean hasMonth1 = e.tags().stream()
                .anyMatch(t -> "month".equals(t.key()) && "1".equals(t.value()));
            return hasMonth1;
        });
        
        // Query January events
        Query janQuery = walletPeriodQuery(walletId, 2024, 1);
        List<StoredEvent> janEvents = eventRepository.query(janQuery, null);
        
        // Should only have January events (period query includes WalletOpened for wallet existence)
        assertThat(janEvents).hasSize(3); // WalletOpened, DepositMade, WithdrawalMade
        assertThat(janEvents).extracting(StoredEvent::type)
            .contains("WalletOpened", "DepositMade", "WithdrawalMade");
        
        // Verify no February events (except WalletOpened which is included for wallet existence)
        assertThat(janEvents).noneMatch(e -> {
            // WalletOpened is included for wallet existence, so skip it
            if ("WalletOpened".equals(e.type())) {
                return false;
            }
            boolean hasMonth2 = e.tags().stream()
                .anyMatch(t -> "month".equals(t.key()) && "2".equals(t.value()));
            return hasMonth2;
        });
    }
    
    @Test
    @DisplayName("Should project balance correctly with WalletStatementOpened and subsequent events")
    void shouldProjectBalanceWithStatementOpening() {
        String walletId = "wallet-closing-books-2";
        
        // February: Open statement
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletStatementOpened")
                .tag("wallet_id", walletId)
                .tag("statement_id", "wallet:" + walletId + ":2024-02")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WalletStatementOpened.of(
                    walletId,
                    "wallet:" + walletId + ":2024-02",
                    2024,
                    2,    // month
                    null, // day
                    null, // hour
                    2000  // Opening balance
                ))
                .build()
        ), AppendCondition.empty());
        
        // February: Deposit
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", walletId)
                .tag("deposit_id", "dep1")
                .tag("year", "2024")
                .tag("month", "2")
                .data(DepositMade.of("dep1", walletId, 500, 2500, "Deposit"))
                .build()
        ), AppendCondition.empty());
        
        // February: Withdrawal
        eventStore.appendIf(List.of(
            AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", walletId)
                .tag("withdrawal_id", "w1")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WithdrawalMade.of("w1", walletId, 200, 2300, "Withdrawal"))
                .build()
        ), AppendCondition.empty());
        
        // Project February balance
        Query query = walletPeriodQuery(walletId, 2024, 2);
        WalletBalanceProjector projector = new WalletBalanceProjector();
        ProjectionResult<WalletBalanceState> result = eventStore.project(
            query,
            Cursor.zero(),
            WalletBalanceState.class,
            List.of(projector)
        );
        
        // Should have final balance after all events
        // Events processed: WalletStatementOpened (2000) -> DepositMade (2500) -> WithdrawalMade (2300)
        assertThat(result.state().isExisting()).isTrue();
        assertThat(result.state().walletId()).isEqualTo(walletId);
        assertThat(result.state().balance()).isEqualTo(2300); // 2000 (opening) + 500 (deposit) - 200 (withdrawal)
    }
    
    @Test
    @DisplayName("Should process events sequentially: WalletStatementOpened first, then other events")
    void shouldProcessEventsSequentially() {
        String walletId = "wallet-closing-books-3";
        
        // Create events in this order
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletStatementOpened")
                .tag("wallet_id", walletId)
                .tag("statement_id", "wallet:" + walletId + ":2024-02")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WalletStatementOpened.of(
                    walletId,
                    "wallet:" + walletId + ":2024-02",
                    2024,
                    2,    // month
                    null, // day
                    null, // hour
                    1000  // Opening balance
                ))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", walletId)
                .tag("deposit_id", "dep1")
                .tag("year", "2024")
                .tag("month", "2")
                .data(DepositMade.of("dep1", walletId, 300, 1300, "First deposit"))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", walletId)
                .tag("deposit_id", "dep2")
                .tag("year", "2024")
                .tag("month", "2")
                .data(DepositMade.of("dep2", walletId, 200, 1500, "Second deposit"))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", walletId)
                .tag("withdrawal_id", "w1")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WithdrawalMade.of("w1", walletId, 100, 1400, "Withdrawal"))
                .build()
        ), AppendCondition.empty());
        
        // Project and verify
        Query query = walletPeriodQuery(walletId, 2024, 2);
        WalletBalanceProjector projector = new WalletBalanceProjector();
        ProjectionResult<WalletBalanceState> result = eventStore.project(
            query,
            Cursor.zero(),
            WalletBalanceState.class,
            List.of(projector)
        );
        
        // Verify sequential processing:
        // 1. WalletStatementOpened: 1000 (opening balance)
        // 2. DepositMade: 1300 (1000 + 300)
        // 3. DepositMade: 1500 (1300 + 200)
        // 4. WithdrawalMade: 1400 (1500 - 100)
        assertThat(result.state().balance()).isEqualTo(1400);
    }
    
    @Test
    @DisplayName("Should handle transfer events with period tags for both wallets")
    void shouldHandleTransferEventsWithPeriodTags() {
        String wallet1 = "wallet-transfer-1";
        String wallet2 = "wallet-transfer-2";
        
        // ===== FEBRUARY 2024 =====
        // Open statements for both wallets
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletStatementOpened")
                .tag("wallet_id", wallet1)
                .tag("statement_id", "wallet:" + wallet1 + ":2024-02")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WalletStatementOpened.of(
                    wallet1,
                    "wallet:" + wallet1 + ":2024-02",
                    2024,
                    2,    // month
                    null, // day
                    null, // hour
                    1000  // Opening balance
                ))
                .build()
        ), AppendCondition.empty());
        
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletStatementOpened")
                .tag("wallet_id", wallet2)
                .tag("statement_id", "wallet:" + wallet2 + ":2024-02")
                .tag("year", "2024")
                .tag("month", "2")
                .data(WalletStatementOpened.of(
                    wallet2,
                    "wallet:" + wallet2 + ":2024-02",
                    2024,
                    2,    // month
                    null, // day
                    null, // hour
                    500  // Opening balance
                ))
                .build()
        ), AppendCondition.empty());
        
        // Transfer from wallet1 to wallet2 in February
        eventStore.appendIf(List.of(
            AppendEvent.builder("MoneyTransferred")
                .tag("transfer_id", "t1")
                .tag("from_wallet_id", wallet1)
                .tag("to_wallet_id", wallet2)
                .tag("from_year", "2024")
                .tag("from_month", "2")
                .tag("to_year", "2024")
                .tag("to_month", "2")
                .data(MoneyTransferred.of(
                    "t1",
                    wallet1,
                    wallet2,
                    200,
                    800,  // wallet1 balance after transfer
                    700,  // wallet2 balance after transfer
                    "Transfer"
                ))
                .build()
        ), AppendCondition.empty());
        
        // ===== VERIFY =====
        // Query wallet1 February events
        Query wallet1Query = walletPeriodQuery(wallet1, 2024, 2);
        List<StoredEvent> wallet1Events = eventRepository.query(wallet1Query, null);
        
        // Should include StatementOpened and MoneyTransferred (as from_wallet)
        assertThat(wallet1Events).hasSize(2);
        assertThat(wallet1Events).extracting(StoredEvent::type)
            .contains("WalletStatementOpened", "MoneyTransferred");
        
        // Query wallet2 February events
        Query wallet2Query = walletPeriodQuery(wallet2, 2024, 2);
        List<StoredEvent> wallet2Events = eventRepository.query(wallet2Query, null);
        
        // Should include StatementOpened and MoneyTransferred (as to_wallet)
        assertThat(wallet2Events).hasSize(2);
        assertThat(wallet2Events).extracting(StoredEvent::type)
            .contains("WalletStatementOpened", "MoneyTransferred");
        
        // Project wallet1 balance
        WalletBalanceProjector projector = new WalletBalanceProjector();
        ProjectionResult<WalletBalanceState> wallet1Result = eventStore.project(
            wallet1Query,
            Cursor.zero(),
            WalletBalanceState.class,
            List.of(projector)
        );
        
        // Should have balance after transfer: 1000 (opening) -> 800 (after transfer)
        assertThat(wallet1Result.state().walletId()).isEqualTo(wallet1);
        assertThat(wallet1Result.state().balance()).isEqualTo(800);
        
        // Project wallet2 balance
        ProjectionResult<WalletBalanceState> wallet2Result = eventStore.project(
            wallet2Query,
            Cursor.zero(),
            WalletBalanceState.class,
            List.of(projector)
        );
        
        // Should have balance after transfer: 500 (opening) -> 700 (after transfer)
        assertThat(wallet2Result.state().walletId()).isEqualTo(wallet2);
        assertThat(wallet2Result.state().balance()).isEqualTo(700);
    }
}

