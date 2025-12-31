package com.crablet.examples.wallet.features.transfer;

import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.wallet.event.DepositMade;
import com.crablet.examples.wallet.event.MoneyTransferred;
import com.crablet.examples.wallet.event.WalletEvent;
import com.crablet.examples.wallet.event.WalletOpened;
import com.crablet.examples.wallet.event.WithdrawalMade;
import com.crablet.examples.wallet.projections.WalletBalanceState;

import java.util.List;

/**
 * Projector for transfer operations - projects balances for both wallets.
 * <p>
 * Thread-safe: creates immutable instances with wallet IDs in constructor.
 */
public class TransferStateProjector implements StateProjector<TransferState> {
    
    private final String fromWalletId;
    private final String toWalletId;
    
    public TransferStateProjector(String fromWalletId, String toWalletId) {
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
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
