package com.wallets.features.transfer;

import com.crablet.core.AppendCondition;
import com.crablet.core.CommandHandler;
import com.crablet.core.CommandResult;
import com.crablet.core.Cursor;
import com.crablet.core.Event;
import com.crablet.core.EventStore;
import com.crablet.core.InputEvent;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.SequenceNumber;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Command handler for transferring money between wallets.
 * 
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
        
        // Idempotency check
        if (transferWasAlreadyProcessed(eventStore, command.transferId())) {
            return CommandResult.emptyWithReason("DUPLICATE_TRANSFER_ID");
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
        
        InputEvent event = InputEvent.of(
            "MoneyTransferred",
            List.of(
                new Tag("transfer_id", command.transferId()),
                new Tag("from_wallet_id", command.fromWalletId()),
                new Tag("to_wallet_id", command.toWalletId())
            ),
            serializeEvent(objectMapper, transfer).getBytes()
        );
        
        // Build condition: cursor + uniqueness
        // DCB Principle: The cursor ensures no events have been appended after projection
        // The failIfEventsMatch only checks for duplicate transfer IDs (idempotency)
        AppendCondition condition = AppendCondition.of(
            transferProjection.cursor(),
            Query.of(QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("transfer_id", command.transferId()))
            ))
        );
        
        return CommandResult.of(List.of(event), condition);
    }
    
    
    /**
     * Check if transfer was already processed (for idempotency handling).
     */
    private boolean transferWasAlreadyProcessed(EventStore store, String transferId) {
        Query query = Query.of(
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("transfer_id", transferId))
            )
        );
        return !store.query(query, null).isEmpty();
    }
    
    /**
     * Project transfer state - balances for both wallets.
     * 
     * DCB Principle: Projects only what TransferMoneyCommand needs.
     * For transfers, we need balances for both wallets (not full WalletState).
     */
    private TransferProjectionResult projectTransferState(EventStore store, TransferMoneyCommand cmd) {
        // Query events for both wallets using OR logic (go-crablet style)
        // Include all events that affect either wallet's balance
        Query walletsQuery = Query.of(List.of(
            // Events for fromWallet
            QueryItem.of(
                List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", cmd.fromWalletId()))
            ),
            // Events for toWallet
            QueryItem.of(
                List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", cmd.toWalletId()))
            ),
            // Transfer events where fromWallet is sender
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("from_wallet_id", cmd.fromWalletId()))
            ),
            // Transfer events where fromWallet is receiver
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("to_wallet_id", cmd.fromWalletId()))
            ),
            // Transfer events where toWallet is sender
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("from_wallet_id", cmd.toWalletId()))
            ),
            // Transfer events where toWallet is receiver
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("to_wallet_id", cmd.toWalletId()))
            )
        ));
        
        List<Event> events = store.query(walletsQuery, null);
        
        // Build balance states for both wallets
        WalletBalanceState fromWallet = balanceProjector.buildBalanceState(events, cmd.fromWalletId());
        WalletBalanceState toWallet = balanceProjector.buildBalanceState(events, cmd.toWalletId());
        
        // Capture cursor for optimistic locking (use latest event)
        Cursor cursor = events.isEmpty() 
            ? Cursor.of(SequenceNumber.zero(), Instant.EPOCH)
            : Cursor.of(events.get(events.size() - 1).position(), events.get(events.size() - 1).occurredAt());

        return new TransferProjectionResult(new TransferState(fromWallet, toWallet), cursor);
    }
    
    
    /**
     * Minimal state for transfer command - balances for both wallets.
     * 
     * DCB Principle: Each command defines its own minimal state projection.
     * This record contains only what TransferMoneyCommand needs to make decisions.
     */
    private record TransferState(
        WalletBalanceState fromWallet,
        WalletBalanceState toWallet
    ) {}
    
    private record TransferProjectionResult(
        TransferState state,
        Cursor cursor
    ) {}
    
    
    @Override
    public String getCommandType() {
        return "transfer_money";
    }
}
