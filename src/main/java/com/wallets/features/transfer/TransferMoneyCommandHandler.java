package com.wallets.features.transfer;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.CommandHandler;
import com.crablet.core.CommandResult;
import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
import com.crablet.core.Query;
import com.crablet.core.QueryBuilder;
import com.crablet.core.SequenceNumber;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.WalletQueryPatterns;
import com.wallets.domain.WalletTags;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.domain.projections.WalletBalanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static com.crablet.core.CommandHandler.serializeEvent;

/**
 * Command handler for transferring money between wallets.
 * <p>
 * DCB Principle: Projects balances for both wallets + concurrency control.
 * Keeps complex transfer logic together for atomicity and consistency.
 */
@Component
public class TransferMoneyCommandHandler implements CommandHandler<TransferMoneyCommand> {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyCommandHandler.class);

    private final ObjectMapper objectMapper;
    private final WalletBalanceProjector balanceProjector;

    public TransferMoneyCommandHandler(ObjectMapper objectMapper, WalletBalanceProjector balanceProjector) {
        this.objectMapper = objectMapper;
        this.balanceProjector = balanceProjector;
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

        AppendEvent event = AppendEvent.builder("MoneyTransferred")
                .tag(WalletTags.TRANSFER_ID, command.transferId())
                .tag(WalletTags.FROM_WALLET_ID, command.fromWalletId())
                .tag(WalletTags.TO_WALLET_ID, command.toWalletId())
                .data(serializeEvent(objectMapper, transfer))
                .build();

        // Build condition: decision model only (cursor-based concurrency control)
        // DCB Principle: Cursor check prevents duplicate charges
        // Note: No idempotency check - cursor advancement detects if operation already succeeded
        AppendCondition condition = transferProjection.decisionModel()
                .toAppendCondition(transferProjection.cursor())
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

        List<StoredEvent> events = store.query(decisionModel, null);

        // Build balance states for both wallets
        WalletBalanceState fromWallet = balanceProjector.buildBalanceState(events, cmd.fromWalletId());
        WalletBalanceState toWallet = balanceProjector.buildBalanceState(events, cmd.toWalletId());

        // Capture cursor for optimistic locking
        Cursor cursor = events.isEmpty()
                ? Cursor.of(SequenceNumber.zero(), Instant.EPOCH)
                : Cursor.of(
                    events.get(events.size() - 1).position(), 
                    events.get(events.size() - 1).occurredAt(),
                    events.get(events.size() - 1).transactionId()
                );

        return new TransferProjectionResult(new TransferState(fromWallet, toWallet), cursor, decisionModel);
    }

    @Override
    public String getCommandType() {
        return "transfer_money";
    }

    /**
     * Minimal state for transfer command - balances for both wallets.
     * <p>
     * DCB Principle: Each command defines its own minimal state projection.
     * This record contains only what TransferMoneyCommand needs to make decisions.
     */
    private record TransferState(
            WalletBalanceState fromWallet,
            WalletBalanceState toWallet
    ) {
    }

    private record TransferProjectionResult(
            TransferState state,
            Cursor cursor,
            Query decisionModel
    ) {
    }
}
