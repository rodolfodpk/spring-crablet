package com.crablet.examples.wallet.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletEvent;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;

import java.util.List;

/**
 * Wallet balance state projection logic for command handlers.
 * <p>
 * This class implements the DCB principle by providing a minimal state projection
 * that only calculates wallet balance and existence, which is what most
 * command handlers need for business rule validation.
 * <p>
 * This is a state projector (in-memory projection), distinct from view projectors
 * that persist materialized views to database tables.
 * <p>
 * Not a singleton - create instances as needed. This class is stateless and thread-safe.
 */
public class WalletBalanceStateProjector implements StateProjector<WalletBalanceState> {

    public WalletBalanceStateProjector() {
    }

    @Override
    public String getId() {
        return "wallet-balance-projector";
    }

    @Override
    public List<String> getEventTypes() {
        return List.of("WalletStatementOpened", "WalletStatementClosed", "WalletOpened", "MoneyTransferred", "DepositMade", "WithdrawalMade");
    }

    @Override
    public WalletBalanceState getInitialState() {
        return new WalletBalanceState("", 0, false);
    }

    @Override
    public WalletBalanceState transition(WalletBalanceState currentState, StoredEvent event, EventDeserializer context) {
        // Deserialize event data as WalletEvent using sealed interface
        WalletEvent walletEvent = context.deserialize(event, WalletEvent.class);

        // Use pattern matching for type-safe event handling
        return switch (walletEvent) {
            case WalletStatementOpened opened -> {
                // WalletStatementOpened sets opening balance, but doesn't indicate wallet existence
                // Wallet existence is determined by WalletOpened event, not statement events
                // Preserve existing state's isExisting flag, only update balance and walletId
                yield new WalletBalanceState(
                        opened.walletId(),
                        opened.openingBalance(),
                        currentState.isExisting() // Preserve existing state
                );
            }

            case WalletOpened opened -> {
                // WalletOpened sets wallet existence - this is the source of truth
                yield new WalletBalanceState(
                        opened.walletId(),
                        opened.initialBalance(),
                        true // Wallet exists
                );
            }

            case MoneyTransferred transfer -> {
                // Check if this transfer affects the current wallet
                if (currentState.walletId().equals(transfer.fromWalletId())) {
                    yield new WalletBalanceState(
                            transfer.fromWalletId(),
                            transfer.fromBalance(),
                            true
                    );
                } else if (currentState.walletId().equals(transfer.toWalletId())) {
                    yield new WalletBalanceState(
                            transfer.toWalletId(),
                            transfer.toBalance(),
                            true
                    );
                } else {
                    yield currentState; // Transfer doesn't affect this wallet
                }
            }

            case DepositMade deposit -> new WalletBalanceState(
                    deposit.walletId(),
                    deposit.newBalance(),
                    true
            );

            case WithdrawalMade withdrawal -> new WalletBalanceState(
                    withdrawal.walletId(),
                    withdrawal.newBalance(),
                    true
            );

            case WalletStatementClosed _ -> {
                // WalletStatementClosed is an audit event - doesn't affect balance projection
                // Balance projection should use closing balance from the closed event if needed,
                // but for active period queries, we only see WalletStatementOpened
                yield currentState;
            }
        };
    }

    /**
     * Project wallet balance using a custom query (DCB pattern with decision model).
     * <p>
     * Callers must provide period-aware queries. Period tags are now mandatory.
     *
     * @param store    The event store to query
     * @param walletId The wallet ID to project balance for
     * @param query    The decision model query to use (must be period-aware)
     * @return ProjectionResult containing WalletBalanceState and cursor for optimistic locking
     */
    public ProjectionResult<WalletBalanceState> projectWalletBalance(EventStore store, String walletId, Query query) {
        // Use new signature: query, cursor, stateType, projectors
        return store.project(query, com.crablet.eventstore.store.Cursor.zero(), WalletBalanceState.class, List.of(this));
    }

}
