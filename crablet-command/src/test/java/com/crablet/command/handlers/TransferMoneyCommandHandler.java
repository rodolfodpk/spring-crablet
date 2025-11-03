package com.crablet.command.handlers;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.examples.wallet.features.transfer.TransferMoneyCommand;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.wallet.domain.WalletQueryPatterns;
import com.crablet.examples.wallet.domain.event.MoneyTransferred;
import com.crablet.examples.wallet.domain.exception.InsufficientFundsException;
import com.crablet.examples.wallet.domain.exception.WalletNotFoundException;
import com.crablet.examples.wallet.features.transfer.TransferState;
import com.crablet.examples.wallet.features.transfer.TransferStateProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.examples.wallet.domain.WalletEventTypes.*;
import static com.crablet.examples.wallet.domain.WalletTags.*;

/**
 * Command handler for transferring money between wallets.
 * <p>
 * DCB Principle: Projects balances for both wallets + concurrency control.
 * Keeps complex transfer logic together for atomicity and consistency.
 */
@Component
public class TransferMoneyCommandHandler implements CommandHandler<TransferMoneyCommand> {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyCommandHandler.class);

    public TransferMoneyCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, TransferMoneyCommand command) {
        // Command is already validated at construction with YAVI

        // Project state (needed for balance calculation)
        TransferProjectionResult transferProjection = projectTransferState(eventStore, command);
        TransferState transferState = transferProjection.state();

        if (!transferState.fromWallet().isExisting()) {
            log.warn("Transfer failed - source wallet not found: fromWalletId={}, toWalletId={}, transferId={}",
                    command.fromWalletId(), command.toWalletId(), command.transferId());
            throw new WalletNotFoundException(command.fromWalletId());
        }
        if (!transferState.toWallet().isExisting()) {
            log.warn("Transfer failed - destination wallet not found: fromWalletId={}, toWalletId={}, transferId={}",
                    command.fromWalletId(), command.toWalletId(), command.transferId());
            throw new WalletNotFoundException(command.toWalletId());
        }
        if (!transferState.fromWallet().hasSufficientFunds(command.amount())) {
            log.warn("Transfer failed - insufficient funds: fromWalletId={}, balance={}, requested={}",
                    command.fromWalletId(), transferState.fromWallet().balance(), command.amount());
            throw new InsufficientFundsException(command.fromWalletId(),
                    transferState.fromWallet().balance(), command.amount());
        }

        // Calculate new balances
        int fromNewBalance = transferState.fromWallet().balance() - command.amount();
        int toNewBalance = transferState.toWallet().balance() + command.amount();

        // Create transfer event
        MoneyTransferred transfer = MoneyTransferred.of(
                command.transferId(),
                command.fromWalletId(),
                command.toWalletId(),
                command.amount(),
                fromNewBalance,
                toNewBalance,
                command.description()
        );

        AppendEvent event = AppendEvent.builder(MONEY_TRANSFERRED)
                .tag(TRANSFER_ID, command.transferId())
                .tag(FROM_WALLET_ID, command.fromWalletId())
                .tag(TO_WALLET_ID, command.toWalletId())
                .data(transfer)
                .build();

        // Transfers are non-commutative - order matters for both wallet balances
        // DCB cursor check REQUIRED: prevents concurrent transfers causing overdrafts
        AppendCondition condition = new AppendConditionBuilder(transferProjection.decisionModel(), transferProjection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }

    /**
     * Project transfer state - balances for both wallets.
     * <p>
     * DCB Principle: Projects only what TransferMoneyCommand needs.
     * For transfers, we need balances for both wallets (not full WalletState).
     */
    private TransferProjectionResult projectTransferState(EventStore store, TransferMoneyCommand cmd) {
        // Use domain-specific query pattern
        Query decisionModel = WalletQueryPatterns.transferDecisionModel(
                cmd.fromWalletId(),
                cmd.toWalletId()
        );
        
        // Create projector instance per projection (immutable, thread-safe)
        TransferStateProjector projector = new TransferStateProjector(cmd.fromWalletId(), cmd.toWalletId());
        
        // Use EventStore's streaming projection with new signature
        com.crablet.eventstore.query.ProjectionResult<TransferState> result = 
            store.project(decisionModel, com.crablet.eventstore.store.Cursor.zero(), TransferState.class, List.of(projector));
        
        return new TransferProjectionResult(result.state(), result.cursor(), decisionModel);
    }

    private record TransferProjectionResult(
            TransferState state,
            com.crablet.eventstore.store.Cursor cursor,
            Query decisionModel
    ) {
    }
}
