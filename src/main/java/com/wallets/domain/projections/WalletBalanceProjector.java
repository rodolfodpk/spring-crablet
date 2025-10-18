package com.wallets.domain.projections;

import com.crablet.core.Cursor;
import com.crablet.core.StoredEvent;
import com.crablet.core.EventStore;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.StateProjector;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 
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
        return List.of("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred");
    }
    
    @Override
    public List<Tag> getTags() {
        return List.of(); // No specific tags - handles all wallet events
    }
    
    @Override
    public WalletBalanceState getInitialState() {
        return new WalletBalanceState("", 0, false);
    }
    
    @Override
    public WalletBalanceState transition(WalletBalanceState currentState, StoredEvent event) {
        // This method is not used in the current implementation
        // as we use the more specific projectWalletBalance method
        return currentState;
    }
    
    /**
     * Project wallet balance state from event store with cursor for DCB concurrency control.
     * 
     * This method queries all events that affect a wallet's balance:
     * - WalletOpened, DepositMade, WithdrawalMade events for the wallet
     * - MoneyTransferred events where the wallet is sender or receiver
     * 
     * @param store The event store to query
     * @param walletId The wallet ID to project balance for
     * @return ProjectionResult containing WalletBalanceState and cursor for optimistic locking
     */
    public ProjectionResult<WalletBalanceState> projectWalletBalance(EventStore store, String walletId) {
        Query query = Query.of(List.of(
            // Events for this wallet (using tags only, like WalletQueryService)
            QueryItem.ofTags(List.of(new Tag("wallet_id", walletId))),
            // Transfer events where this wallet is sender
            QueryItem.ofTags(List.of(new Tag("from_wallet_id", walletId))),
            // Transfer events where this wallet is receiver
            QueryItem.ofTags(List.of(new Tag("to_wallet_id", walletId)))
        ));
        
        List<StoredEvent> events = store.query(query, null);
        WalletBalanceState state = buildBalanceState(events, walletId);
        
        // Capture cursor for optimistic locking
        Cursor cursor = events.isEmpty()
            ? Cursor.zero()
            : Cursor.of(
                events.get(events.size() - 1).position(),
                events.get(events.size() - 1).occurredAt(),
                events.get(events.size() - 1).transactionId()
            );
        
        return ProjectionResult.of(state, cursor);
    }
    
    /**
     * Build balance state from a list of events for a specific wallet.
     * 
     * This method processes events in order and extracts the final balance
     * and existence status. It handles all wallet-related events that affect balance.
     * 
     * @param events List of events to process
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
     * @param walletId The wallet ID to process events for
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
        
        WalletEvent[] walletEvents = batchDeserializeWalletEvents(eventDataList);
        
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
     * Batch deserialize multiple event data arrays to WalletEvent array.
     * Uses Jackson's polymorphic support for efficient batch processing.
     * 
     * @param eventDataList List of raw event data bytes
     * @return Array of deserialized WalletEvent objects
     * @throws Exception if any deserialization fails
     */
    private WalletEvent[] batchDeserializeWalletEvents(List<byte[]> eventDataList) throws Exception {
        if (eventDataList.isEmpty()) {
            return new WalletEvent[0];
        }
        
        // Build JSON array from event data
        StringBuilder jsonArray = new StringBuilder("[");
        for (int i = 0; i < eventDataList.size(); i++) {
            if (i > 0) jsonArray.append(",");
            jsonArray.append(new String(eventDataList.get(i), java.nio.charset.StandardCharsets.UTF_8));
        }
        jsonArray.append("]");
        
        // Deserialize entire array in one pass
        return objectMapper.readValue(jsonArray.toString(), WalletEvent[].class);
    }
}
