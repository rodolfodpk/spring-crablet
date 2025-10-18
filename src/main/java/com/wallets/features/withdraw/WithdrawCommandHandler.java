package com.wallets.features.withdraw;

import com.crablet.core.AppendCondition;
import com.crablet.core.CommandHandler;
import com.crablet.core.CommandResult;
import com.crablet.core.EventStore;
import com.crablet.core.AppendEvent;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.domain.projections.WalletBalanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.core.CommandHandler.serializeEvent;

/**
 * Command handler for withdrawing money from wallets.
 * 
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 */
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {
    
    private static final Logger log = LoggerFactory.getLogger(WithdrawCommandHandler.class);
    
    private final ObjectMapper objectMapper;
    private final WalletBalanceProjector balanceProjector;
    
    public WithdrawCommandHandler(ObjectMapper objectMapper, WalletBalanceProjector balanceProjector) {
        this.objectMapper = objectMapper;
        this.balanceProjector = balanceProjector;
    }
    
    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // Command is already validated at construction with YAVI
        
        // Project state (needed for balance calculation)
        ProjectionResult<WalletBalanceState> projection = 
            balanceProjector.projectWalletBalance(eventStore, command.walletId());
        WalletBalanceState state = projection.state();
        
        if (!state.isExisting()) {
            log.warn("Withdrawal failed - wallet not found: walletId={}, withdrawalId={}", 
                command.walletId(), command.withdrawalId());
            throw new WalletNotFoundException(command.walletId());
        }
        if (!state.hasSufficientFunds(command.amount())) {
            log.warn("Withdrawal failed - insufficient funds: walletId={}, balance={}, requested={}", 
                command.walletId(), state.balance(), command.amount());
            throw new InsufficientFundsException(command.walletId(), state.balance(), command.amount());
        }
        
        // Idempotency check
        if (withdrawalWasAlreadyProcessed(eventStore, command.withdrawalId())) {
            return CommandResult.emptyWithReason("DUPLICATE_WITHDRAWAL_ID");
        }
        
        int newBalance = state.balance() - command.amount();
        
        WithdrawalMade withdrawal = WithdrawalMade.of(
            command.withdrawalId(),
            command.walletId(),
            command.amount(),
            newBalance,
            command.description()
        );
        
        AppendEvent event = AppendEvent.of(
            "WithdrawalMade",
            List.of(
                new Tag("wallet_id", command.walletId()),
                new Tag("withdrawal_id", command.withdrawalId())
            ),
            serializeEvent(objectMapper, withdrawal).getBytes()
        );
        
        // Build condition: cursor + uniqueness
        AppendCondition condition = AppendCondition.of(
            projection.cursor(),
            Query.of(QueryItem.of(
                List.of("WithdrawalMade"),
                List.of(new Tag("withdrawal_id", command.withdrawalId()))
            ))
        );
        
        return CommandResult.of(List.of(event), condition);
    }
    
    
    /**
     * Check if withdrawal was already processed (for idempotency handling).
     */
    private boolean withdrawalWasAlreadyProcessed(EventStore store, String withdrawalId) {
        Query query = Query.of(
            QueryItem.of(
                List.of("WithdrawalMade"), 
                List.of(new Tag("withdrawal_id", withdrawalId))
            )
        );
        return !store.query(query, null).isEmpty();
    }
    
    
    @Override
    public String getCommandType() {
        return "withdraw";
    }
}
