package com.wallets.features.deposit;

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
import com.wallets.domain.event.DepositMade;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.domain.projections.WalletBalanceState;
import com.wallets.domain.WalletQueryPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.core.CommandHandler.serializeEvent;

/**
 * Command handler for depositing money into wallets.
 * 
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 */
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    
    private static final Logger log = LoggerFactory.getLogger(DepositCommandHandler.class);
    
    private final ObjectMapper objectMapper;
    private final WalletBalanceProjector balanceProjector;
    
    public DepositCommandHandler(ObjectMapper objectMapper, WalletBalanceProjector balanceProjector) {
        this.objectMapper = objectMapper;
        this.balanceProjector = balanceProjector;
    }
    
    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // Command is already validated at construction with YAVI
        
        // Use domain-specific decision model query
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        
        // Project state (needed for balance calculation)
        ProjectionResult<WalletBalanceState> projection = 
            balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
        WalletBalanceState state = projection.state();
        
        if (!state.isExisting()) {
            log.warn("Deposit failed - wallet not found: walletId={}, depositId={}", 
                command.walletId(), command.depositId());
            throw new WalletNotFoundException(command.walletId());
        }
        
        // Idempotency check
        if (depositWasAlreadyProcessed(eventStore, command.depositId())) {
            return CommandResult.emptyWithReason("DUPLICATE_DEPOSIT_ID");
        }
        
        int newBalance = state.balance() + command.amount();
        
        DepositMade deposit = DepositMade.of(
            command.depositId(),
            command.walletId(),
            command.amount(),
            newBalance,
            command.description()
        );
        
        AppendEvent event = AppendEvent.of(
            "DepositMade",
            List.of(
                new Tag("wallet_id", command.walletId()),
                new Tag("deposit_id", command.depositId())
            ),
            serializeEvent(objectMapper, deposit).getBytes()
        );
        
        // Build condition: decision model + idempotency
        // DCB Principle: failIfEventsMatch includes same query used for projection
        AppendCondition condition = decisionModel
            .toAppendCondition(projection.cursor())
            .withIdempotencyCheck(
                "DepositMade",
                new Tag("deposit_id", command.depositId())
            )
            .build();
        
        return CommandResult.of(List.of(event), condition);
    }
    
    
    /**
     * Check if deposit was already processed (for idempotency handling).
     */
    private boolean depositWasAlreadyProcessed(EventStore store, String depositId) {
        Query query = Query.of(
            QueryItem.of(
                List.of("DepositMade"), 
                List.of(new Tag("deposit_id", depositId))
            )
        );
        return !store.query(query, null).isEmpty();
    }
    
    
    @Override
    public String getCommandType() {
        return "deposit";
    }
}
