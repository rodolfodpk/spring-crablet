package com.wallets.features.withdraw;

import com.crablet.eventstore.AppendCondition;
import com.crablet.eventstore.AppendConditionBuilder;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.CommandHandler;
import com.crablet.eventstore.CommandResult;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.ProjectionResult;
import com.crablet.eventstore.Query;
import com.wallets.domain.WalletQueryPatterns;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.domain.projections.WalletBalanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.wallets.domain.WalletEventTypes.*;
import static com.wallets.domain.WalletTags.*;

/**
 * Command handler for withdrawing money from wallets.
 * <p>
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 */
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {

    private static final Logger log = LoggerFactory.getLogger(WithdrawCommandHandler.class);

    private final WalletBalanceProjector balanceProjector;

    public WithdrawCommandHandler(WalletBalanceProjector balanceProjector) {
        this.balanceProjector = balanceProjector;
    }

    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // Command is already validated at construction with YAVI

        // Use domain-specific decision model query
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());

        // Project state (needed for balance calculation)
        ProjectionResult<WalletBalanceState> projection =
                balanceProjector.projectWalletBalance(eventStore, command.walletId(), decisionModel);
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

        // Build condition: decision model only (cursor-based concurrency control)
        // DCB Principle: Cursor check prevents duplicate charges
        // Note: No idempotency check - cursor advancement detects if operation already succeeded
        AppendCondition condition = new AppendConditionBuilder(decisionModel, projection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }

    @Override
    public String getCommandType() {
        return "withdraw";
    }
}
