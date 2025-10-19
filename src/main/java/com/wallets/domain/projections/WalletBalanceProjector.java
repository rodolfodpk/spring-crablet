package com.wallets.domain.projections;

import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.WalletQueryPatterns;
import com.wallets.domain.event.DepositMade;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletEvent;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared wallet balance projection logic for command handlers.
 * <p>
 * This class implements the DCB principle by providing a minimal projection
 * that only calculates wallet balance and existence, which is what most
 * command handlers need for business rule validation.
 */
@Component
public class WalletBalanceProjector implements StateProjector<WalletBalanceState> {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceProjector.class);

    private final ObjectMapper objectMapper;

    public WalletBalanceProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
    public List<Tag> getTags() {
        // This projector doesn't filter by specific tags - it processes all wallet events
        return List.of();
    }

    @Override
    public WalletBalanceState getInitialState() {
        return new WalletBalanceState("", 0, false);
    }

    @Override
    public WalletBalanceState transition(WalletBalanceState currentState, StoredEvent event) {
        // Parse event data as WalletEvent using sealed interface
        WalletEvent walletEvent;
        try {
            walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize WalletEvent", e);
        }

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
        try {
            // Use new batch deserialization method for better performance
            byte[] eventsJsonArray = store.queryAsJsonArray(query, null);
            WalletEvent[] walletEvents = objectMapper.readValue(eventsJsonArray, WalletEvent[].class);
            
            WalletBalanceState state = processEventsFromWalletEvents(walletEvents, walletId);

            // Capture cursor for optimistic locking - we need to get the last event position
            // Since we don't have StoredEvent objects, we'll use a zero cursor for now
            // TODO: Consider if we need to modify queryAsJsonArray to also return cursor info
            Cursor cursor = Cursor.zero();

            return ProjectionResult.of(state, cursor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to project wallet balance from EventStore", e);
        }
    }

    /**
     * Build balance state from a list of events for a specific wallet.
     * <p>
     * This method processes events in order and extracts the final balance
     * and existence status. It handles all wallet-related events that affect balance.
     *
     * @param events   List of events to process
     * @param walletId The wallet ID to build state for
     * @return WalletBalanceState containing balance and existence
     */
    public WalletBalanceState buildBalanceState(List<StoredEvent> events, String walletId) {
        // Filter events relevant to this wallet (for transfer scenarios)
        // Optimized: Single iteration through tags instead of three separate stream operations
        List<StoredEvent> relevantEvents = events.stream()
                .filter(event -> {
                    // Check if event is relevant to this wallet with single tag iteration
                    for (Tag tag : event.tags()) {
                        String key = tag.key();
                        if (tag.value().equals(walletId) &&
                                (key.equals("wallet_id") || key.equals("from_wallet_id") || key.equals("to_wallet_id"))) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();

        if (relevantEvents.isEmpty()) {
            return new WalletBalanceState(walletId, 0, false);
        }

        try {
            return processEvents(relevantEvents, walletId);
        } catch (Exception e) {
            log.error("Failed to build balance state for wallet {}: {}", walletId, e.getMessage(), e);
            // Return empty state on any parsing error to ensure consistency
            return new WalletBalanceState(walletId, 0, false);
        }
    }

    /**
     * Process events to build wallet balance state.
     * This method assumes all events can be parsed successfully.
     *
     * @param relevantEvents List of events relevant to the wallet
     * @param walletId       The wallet ID to process events for
     * @return WalletBalanceState with calculated balance and existence
     * @throws Exception if any event fails to parse
     */
    private WalletBalanceState processEvents(List<StoredEvent> relevantEvents, String walletId) throws Exception {
        if (relevantEvents.isEmpty()) {
            return new WalletBalanceState(walletId, 0, false);
        }

        // Batch deserialize all events for better performance
        List<byte[]> eventDataList = relevantEvents.stream()
                .map(StoredEvent::data)
                .toList();

        // Build JSON array manually for backward compatibility
        StringBuilder jsonArray = new StringBuilder("[");
        for (int i = 0; i < eventDataList.size(); i++) {
            if (i > 0) jsonArray.append(",");
            jsonArray.append(new String(eventDataList.get(i), java.nio.charset.StandardCharsets.UTF_8));
        }
        jsonArray.append("]");

        WalletEvent[] walletEvents = objectMapper.readValue(jsonArray.toString(), WalletEvent[].class);

        int balance = 0;
        boolean exists = false;

        for (WalletEvent walletEvent : walletEvents) {
            switch (walletEvent) {
                case WalletOpened opened when walletId.equals(opened.walletId()) -> {
                    balance = opened.initialBalance();
                    exists = true;
                }
                case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) -> {
                    balance = transfer.fromBalance();
                }
                case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) -> {
                    balance = transfer.toBalance();
                }
                case DepositMade deposit when walletId.equals(deposit.walletId()) -> {
                    balance = deposit.newBalance();
                }
                case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) -> {
                    balance = withdrawal.newBalance();
                }
                default -> {
                    // Event not relevant to this wallet - this shouldn't happen after filtering
                    log.warn("Unexpected event type {} for wallet {}", walletEvent.getClass().getSimpleName(), walletId);
                }
            }
        }

        return new WalletBalanceState(walletId, balance, exists);
    }

    /**
     * Process wallet events directly from deserialized WalletEvent array.
     * This method assumes all events can be parsed successfully.
     *
     * @param walletEvents Array of deserialized wallet events
     * @param walletId    The wallet ID to process events for
     * @return WalletBalanceState with calculated balance and existence
     */
    private WalletBalanceState processEventsFromWalletEvents(WalletEvent[] walletEvents, String walletId) {
        if (walletEvents.length == 0) {
            return new WalletBalanceState(walletId, 0, false);
        }

        int balance = 0;
        boolean exists = false;

        for (WalletEvent walletEvent : walletEvents) {
            switch (walletEvent) {
                case WalletOpened opened when walletId.equals(opened.walletId()) -> {
                    balance = opened.initialBalance();
                    exists = true;
                }
                case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) -> {
                    balance = transfer.fromBalance();
                }
                case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) -> {
                    balance = transfer.toBalance();
                }
                case DepositMade deposit when walletId.equals(deposit.walletId()) -> {
                    balance = deposit.newBalance();
                }
                case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) -> {
                    balance = withdrawal.newBalance();
                }
                default -> {
                    // Event not relevant to this wallet - this shouldn't happen after filtering
                    log.warn("Unexpected event type {} for wallet {}", walletEvent.getClass().getSimpleName(), walletId);
                }
            }
        }

        return new WalletBalanceState(walletId, balance, exists);
    }

}
