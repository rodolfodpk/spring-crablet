package com.wallets.features.query;

import com.crablet.core.Event;
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

import java.util.List;

/**
 * WalletStateProjector reconstructs wallet state from events.
 * This handles both WalletOpened and MoneyTransferred events.
 */
public class WalletStateProjector implements StateProjector<WalletState> {
    
    private static final Logger log = LoggerFactory.getLogger(WalletStateProjector.class);
    
    private final String walletId;
    private final ObjectMapper objectMapper;
    
    public WalletStateProjector(String walletId, ObjectMapper objectMapper) {
        this.walletId = walletId;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getId() {
        return "wallet-" + walletId;
    }
    
    @Override
    public List<String> getEventTypes() {
        return List.of("WalletOpened", "MoneyTransferred", "DepositMade", "WithdrawalMade");
    }
    
    @Override
    public List<Tag> getTags() {
        return List.of(new Tag("wallet_id", walletId));
    }
    
    @Override
    public WalletState getInitialState() {
        return WalletState.empty();
    }
    
    /**
     * Project wallet state from multiple events efficiently using batch deserialization.
     * This is the optimized method that should be used instead of individual transition() calls.
     * 
     * @param events List of events to project from
     * @return Final wallet state after projecting all events
     */
    public WalletState project(List<Event> events) {
        if (events.isEmpty()) {
            return getInitialState();
        }
        
        // Batch deserialize all events for better performance
        List<byte[]> eventDataList = events.stream()
            .map(Event::data)
            .toList();
        
        WalletEvent[] walletEvents = batchDeserializeWalletEvents(eventDataList);
        
        WalletState state = getInitialState();
        
        // Process each deserialized event
        for (int i = 0; i < walletEvents.length; i++) {
            WalletEvent walletEvent = walletEvents[i];
            
            // Apply the same logic as transition() but with pre-deserialized events
            state = switch (walletEvent) {
                case WalletOpened opened when walletId.equals(opened.walletId()) -> 
                    new WalletState(
                        opened.walletId(),
                        opened.owner(),
                        opened.initialBalance(),
                        opened.openedAt(),
                        opened.openedAt()
                    );
                
                case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) -> 
                    state.withBalance(transfer.fromBalance(), transfer.transferredAt());
                
                case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) -> 
                    state.withBalance(transfer.toBalance(), transfer.transferredAt());
                
                case DepositMade deposit when walletId.equals(deposit.walletId()) -> 
                    state.withBalance(deposit.newBalance(), deposit.depositedAt());
                
                case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) -> 
                    state.withBalance(withdrawal.newBalance(), withdrawal.withdrawnAt());
                
                default -> state; // Event not relevant to this wallet
            };
        }
        
        return state;
    }
    
    /**
     * Batch deserialize wallet events using Jackson's polymorphic support.
     */
    private WalletEvent[] batchDeserializeWalletEvents(List<byte[]> eventDataList) {
        if (eventDataList.isEmpty()) {
            return new WalletEvent[0];
        }
        
        try {
            StringBuilder jsonArray = new StringBuilder("[");
            for (int i = 0; i < eventDataList.size(); i++) {
                if (i > 0) jsonArray.append(",");
                jsonArray.append(new String(eventDataList.get(i), java.nio.charset.StandardCharsets.UTF_8));
            }
            jsonArray.append("]");
            
            return objectMapper.readValue(jsonArray.toString(), WalletEvent[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch deserialize WalletEvents", e);
        }
    }
    
    @Override
    public WalletState transition(WalletState currentState, Event event) {
        // Parse event data as WalletEvent using sealed interface
        // Since we fail-fast on any deserialization error, no try-catch needed
        WalletEvent walletEvent;
        try {
            walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize WalletEvent", e);
        }
        
        // Use pattern matching for type-safe event handling
        return switch (walletEvent) {
            case WalletOpened opened when walletId.equals(opened.walletId()) -> 
                new WalletState(
                    opened.walletId(),
                    opened.owner(),
                    opened.initialBalance(),
                    opened.openedAt(),
                    opened.openedAt()
                );
            
            case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) -> 
                currentState.withBalance(transfer.fromBalance(), transfer.transferredAt());
            
            case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) -> 
                currentState.withBalance(transfer.toBalance(), transfer.transferredAt());
            
            case DepositMade deposit when walletId.equals(deposit.walletId()) -> 
                currentState.withBalance(deposit.newBalance(), deposit.depositedAt());
            
            case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) -> 
                currentState.withBalance(withdrawal.newBalance(), withdrawal.withdrawnAt());
            
            
            default -> currentState; // Event not relevant to this wallet
        };
    }
}
