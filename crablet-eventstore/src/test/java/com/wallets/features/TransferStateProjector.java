package com.wallets.features.transfer;

import com.crablet.eventstore.EventDeserializer;
import com.crablet.eventstore.StateProjector;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.wallets.domain.event.DepositMade;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletEvent;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.domain.projections.WalletBalanceState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * State for transfer operations - balances for both wallets.
 */
record TransferState(
    WalletBalanceState fromWallet,
    WalletBalanceState toWallet
) {}

@Component
public class TransferStateProjector implements StateProjector<TransferState> {
    
    private String fromWalletId;
    private String toWalletId;
    
    public TransferStateProjector() {
    }
    
    public TransferStateProjector forWallets(String fromWalletId, String toWalletId) {
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        return this;
    }
    
    @Override
    public String getId() {
        return "transfer-state-projector";
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
    public TransferState getInitialState() {
        return new TransferState(
            new WalletBalanceState(fromWalletId, 0, false),
            new WalletBalanceState(toWalletId, 0, false)
        );
    }
    
    @Override
    public TransferState transition(TransferState current, StoredEvent event, EventDeserializer context) {
        WalletEvent walletEvent = context.deserialize(event, WalletEvent.class);
        
        WalletBalanceState fromWallet = updateWalletBalance(current.fromWallet(), fromWalletId, walletEvent);
        WalletBalanceState toWallet = updateWalletBalance(current.toWallet(), toWalletId, walletEvent);
        
        return new TransferState(fromWallet, toWallet);
    }
    
    private WalletBalanceState updateWalletBalance(WalletBalanceState current, String walletId, WalletEvent event) {
        return switch (event) {
            case WalletOpened opened when walletId.equals(opened.walletId()) ->
                new WalletBalanceState(walletId, opened.initialBalance(), true);
            case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) ->
                new WalletBalanceState(walletId, transfer.fromBalance(), true);
            case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) ->
                new WalletBalanceState(walletId, transfer.toBalance(), true);
            case DepositMade deposit when walletId.equals(deposit.walletId()) ->
                new WalletBalanceState(walletId, deposit.newBalance(), true);
            case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) ->
                new WalletBalanceState(walletId, withdrawal.newBalance(), true);
            default -> current;
        };
    }
}
