package com.crablet.examples.wallet.domain.projections;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.examples.wallet.domain.WalletQueryPatterns;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.event.MoneyTransferred;
import com.crablet.examples.wallet.domain.event.WalletEvent;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import com.crablet.examples.wallet.domain.event.WithdrawalMade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wallet balance projection logic for command handlers.
 * <p>
 * This class implements the DCB principle by providing a minimal projection
 * that only calculates wallet balance and existence, which is what most
 * command handlers need for business rule validation.
 * <p>
 * Not a singleton - create instances as needed. This class is stateless and thread-safe.
 */
public class WalletBalanceProjector implements StateProjector<WalletBalanceState> {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceProjector.class);

    public WalletBalanceProjector() {
    }

    @Override
    public String getId() {
        return "wallet-balance-projector";
    }

    @Override
    public List<String> getEventTypes() {
        return List.of("WalletOpened", "MoneyTransferred", "DepositMade", "WithdrawalMade");
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
            case WalletOpened opened -> new WalletBalanceState(
                    opened.walletId(),
                    opened.initialBalance(),
                    true
            );

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

            default -> currentState; // Event not relevant
        };
    }

    /**
     * Project wallet balance state from event store with cursor for DCB concurrency control.
     * <p>
     * This method queries all events that affect a wallet's balance using WalletQueryPatterns
     * for consistency with DCB decision model queries.
     *
     * @param store    The event store to query
     * @param walletId The wallet ID to project balance for
     * @return ProjectionResult containing WalletBalanceState and cursor for optimistic locking
     */
    public ProjectionResult<WalletBalanceState> projectWalletBalance(EventStore store, String walletId) {
        // Use WalletQueryPatterns for consistency with DCB decision model queries
        Query query = WalletQueryPatterns.singleWalletDecisionModel(walletId);
        return projectWalletBalance(store, walletId, query);
    }

    /**
     * Project wallet balance using a custom query (DCB pattern with decision model).
     *
     * @param store    The event store to query
     * @param walletId The wallet ID to project balance for
     * @param query    The decision model query to use
     * @return ProjectionResult containing WalletBalanceState and cursor for optimistic locking
     */
    public ProjectionResult<WalletBalanceState> projectWalletBalance(EventStore store, String walletId, Query query) {
        // Use new signature: query, cursor, stateType, projectors
        return store.project(query, com.crablet.eventstore.store.Cursor.zero(), WalletBalanceState.class, List.of(this));
    }

}
