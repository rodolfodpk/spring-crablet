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
    
    @Override
    public WalletState transition(WalletState currentState, Event event) {
        try {
            // Parse event data as WalletEvent using sealed interface
            WalletEvent walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
            
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
        } catch (Exception e) {
            // If parsing fails, return current state unchanged
            log.error("Failed to parse event data: {}", e.getMessage(), e);
            return currentState;
        }
    }
}
