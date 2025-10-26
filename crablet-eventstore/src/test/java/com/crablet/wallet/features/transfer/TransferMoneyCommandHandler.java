package com.crablet.wallet.features.transfer;

import com.crablet.dcb.AppendCondition;
import com.crablet.dcb.AppendConditionBuilder;
import com.crablet.store.AppendEvent;
import com.crablet.commands.CommandHandler;
import com.crablet.commands.CommandResult;
import com.crablet.store.EventStore;
import com.crablet.query.Query;
import com.crablet.wallet.domain.WalletQueryPatterns;
import com.crablet.wallet.domain.event.MoneyTransferred;
import com.crablet.wallet.domain.exception.InsufficientFundsException;
import com.crablet.wallet.domain.exception.WalletNotFoundException;
import com.crablet.wallet.domain.projections.WalletBalanceProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.wallet.domain.WalletEventTypes.*;
import static com.crablet.wallet.domain.WalletTags.*;

/**
 * Command handler for transferring money between wallets.
 * <p>
 * DCB Principle: Projects balances for both wallets + concurrency control.
 * Keeps complex transfer logic together for atomicity and consistency.
 */
@Component
public class TransferMoneyCommandHandler implements CommandHandler<TransferMoneyCommand> {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyCommandHandler.class);

    private final WalletBalanceProjector balanceProjector;
    private final TransferStateProjector transferProjector;

    public TransferMoneyCommandHandler(WalletBalanceProjector balanceProjector, TransferStateProjector transferProjector) {
        this.balanceProjector = balanceProjector;
        this.transferProjector = transferProjector;
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

        // Build condition: decision model only (cursor-based concurrency control)
        // DCB Principle: Cursor check prevents duplicate charges
        // Note: No idempotency check - cursor advancement detects if operation already succeeded
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
        
        // Configure projector for these specific wallets
        transferProjector.forWallets(cmd.fromWalletId(), cmd.toWalletId());
        
        // Use EventStore's streaming projection with new signature
        com.crablet.query.ProjectionResult<TransferState> result = 
            store.project(decisionModel, com.crablet.store.Cursor.zero(), TransferState.class, List.of(transferProjector));
        
        return new TransferProjectionResult(result.state(), result.cursor(), decisionModel);
    }

    @Override
    public String getCommandType() {
        return "transfer_money";
    }

    private record TransferProjectionResult(
            TransferState state,
            com.crablet.store.Cursor cursor,
            Query decisionModel
    ) {
    }
}
