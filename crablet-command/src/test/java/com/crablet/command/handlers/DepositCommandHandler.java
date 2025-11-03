package com.crablet.command.handlers;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.examples.wallet.features.deposit.DepositCommand;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.Cursor;
import com.crablet.examples.wallet.domain.WalletQueryPatterns;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.exception.WalletNotFoundException;
import com.crablet.examples.wallet.domain.projections.WalletBalanceProjector;
import com.crablet.examples.wallet.domain.projections.WalletBalanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.examples.wallet.domain.WalletEventTypes.*;
import static com.crablet.examples.wallet.domain.WalletTags.*;

/**
 * Command handler for depositing money into wallets.
 * <p>
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 */
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {

    private static final Logger log = LoggerFactory.getLogger(DepositCommandHandler.class);

    public DepositCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // Command is already validated at construction with YAVI

        // Project state to validate wallet exists and get current balance
        WalletBalanceProjector projector = new WalletBalanceProjector();
        Query query = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                query, Cursor.zero(), WalletBalanceState.class, List.of(projector));
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

        // Deposits are commutative operations - order doesn't matter
        // Balance: $100 → +$10 → +$20 = $130 (same as +$20 → +$10)
        // No DCB cursor check needed - allows parallel deposits on same wallet
        // Only requirement: wallet must exist (validated above)
        // Idempotency via deposit_id tag prevents duplicates
        AppendCondition condition = AppendCondition.empty();

        return CommandResult.of(List.of(event), condition);
    }
}
