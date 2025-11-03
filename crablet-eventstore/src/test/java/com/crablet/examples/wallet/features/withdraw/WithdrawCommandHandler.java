package com.crablet.examples.wallet.features.withdraw;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.command.CommandHandler;
import com.crablet.eventstore.command.CommandResult;
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.wallet.domain.WalletQueryPatterns;
import com.crablet.examples.wallet.domain.event.WithdrawalMade;
import com.crablet.examples.wallet.domain.exception.InsufficientFundsException;
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
 * Command handler for withdrawing money from wallets.
 * <p>
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 */
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {

    private static final Logger log = LoggerFactory.getLogger(WithdrawCommandHandler.class);

    public WithdrawCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // Command is already validated at construction with YAVI

        // Use domain-specific decision model query
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());

        // Project state (needed for balance calculation)
        WalletBalanceProjector projector = new WalletBalanceProjector();
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                decisionModel, Cursor.zero(), WalletBalanceState.class, List.of(projector));
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

        int newBalance = state.balance() - command.amount();

        WithdrawalMade withdrawal = WithdrawalMade.of(
                command.withdrawalId(),
                command.walletId(),
                command.amount(),
                newBalance,
                command.description()
        );

        AppendEvent event = AppendEvent.builder(WITHDRAWAL_MADE)
                .tag(WALLET_ID, command.walletId())
                .tag(WITHDRAWAL_ID, command.withdrawalId())
                .data(withdrawal)
                .build();

        // Withdrawals are non-commutative - order matters for balance validation
        // DCB cursor check REQUIRED: prevents concurrent withdrawals exceeding balance
        // Example: $100 balance, two $80 withdrawals - both see $100, but only one should succeed
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }
}
