package com.crablet.wallet.features.deposit;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.commands.CommandHandler;
import com.crablet.eventstore.commands.CommandResult;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.wallet.domain.WalletQueryPatterns;
import com.crablet.wallet.domain.event.DepositMade;
import com.crablet.wallet.domain.exception.WalletNotFoundException;
import com.crablet.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.wallet.domain.projections.WalletBalanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.wallet.domain.WalletEventTypes.*;
import static com.crablet.wallet.domain.WalletTags.*;

/**
 * Command handler for depositing money into wallets.
 * <p>
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 */
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {

    private static final Logger log = LoggerFactory.getLogger(DepositCommandHandler.class);

    private final WalletBalanceProjector balanceProjector;

    public DepositCommandHandler(WalletBalanceProjector balanceProjector) {
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

        int newBalance = state.balance() + command.amount();

        DepositMade deposit = DepositMade.of(
                command.depositId(),
                command.walletId(),
                command.amount(),
                newBalance,
                command.description()
        );

        AppendEvent event = AppendEvent.builder(DEPOSIT_MADE)
                .tag(WALLET_ID, command.walletId())
                .tag(DEPOSIT_ID, command.depositId())
                .data(deposit)
                .build();

        // Build condition: decision model only (cursor-based concurrency control)
        // DCB Principle: Cursor check prevents duplicate charges
        // Note: No idempotency check - cursor advancement detects if operation already succeeded
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }

    @Override
    public String getCommandType() {
        return "deposit";
    }
}
