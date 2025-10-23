package com.wallets.features.query;

import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WalletStateProjector reconstructs wallet state from events.
 * This handles both WalletOpened and MoneyTransferred events.
 */
@Component
public class WalletStateProjector implements StateProjector<WalletState> {

    private final ObjectMapper objectMapper;

    public WalletStateProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getId() {
        return "wallet-state-projector";
    }

    @Override
    public List<String> getEventTypes() {
        return List.of("WalletOpened", "MoneyTransferred", "DepositMade", "WithdrawalMade");
    }

    @Override
    public List<Tag> getTags() {
        return List.of(); // Filter by query, not projector
    }

    @Override
    public WalletState getInitialState() {
        return WalletState.empty();
    }

    @Override
    public WalletState transition(WalletState currentState, StoredEvent event) {
        // Parse event data as WalletEvent using sealed interface
        WalletEvent walletEvent;
        try {
            walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize WalletEvent", e);
        }

        // Use pattern matching for type-safe event handling
        return switch (walletEvent) {
            case WalletOpened opened -> {
                // Only create new wallet if state is empty, otherwise check if it's for this wallet
                if (currentState.isEmpty()) {
                    yield new WalletState(opened.walletId(), opened.owner(),
                        opened.initialBalance(), opened.openedAt(), opened.openedAt());
                } else if (currentState.walletId().equals(opened.walletId())) {
                    // Wallet already opened (shouldn't happen but handle gracefully)
                    yield currentState;
                } else {
                    // Different wallet - ignore
                    yield currentState;
                }
            }

            case MoneyTransferred transfer -> {
                // Check if this transfer affects the current wallet
                if (currentState.walletId().equals(transfer.fromWalletId())) {
                    // This is the sender
                    yield currentState.withBalance(transfer.fromBalance(), transfer.transferredAt());
                } else if (currentState.walletId().equals(transfer.toWalletId())) {
                    // This is the receiver
                    yield currentState.withBalance(transfer.toBalance(), transfer.transferredAt());
                } else {
                    // Transfer doesn't affect this wallet
                    yield currentState;
                }
            }

            case DepositMade deposit ->
                currentState.withBalance(deposit.newBalance(), deposit.depositedAt());

            case WithdrawalMade withdrawal ->
                currentState.withBalance(withdrawal.newBalance(), withdrawal.withdrawnAt());

            default -> currentState;
        };
    }
}
