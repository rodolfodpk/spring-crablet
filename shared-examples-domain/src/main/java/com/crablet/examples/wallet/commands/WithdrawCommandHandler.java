package com.crablet.examples.wallet.commands;

import com.crablet.command.CommandDecision;
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.examples.wallet.exceptions.InsufficientFundsException;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.WITHDRAWAL_ID;

/**
 * Command handler for withdrawing money from wallets.
 * <p>
 * DCB Principle: Non-commutative operation — order matters for balance validation.
 * StreamPosition-based DCB check prevents concurrent withdrawals from exceeding the balance.
 * <p>
 * Retry safety: the {@link #handle} override checks for a duplicate withdrawal_id before
 * any business guards run. This is necessary because balance checks throw
 * {@link com.crablet.examples.wallet.exceptions.InsufficientFundsException} on retry
 * (the balance is already reduced), which would happen before the store-level append and
 * prevent idempotent detection. The pre-check returns {@link CommandDecision.NoOp} early,
 * short-circuiting {@link #decide} entirely.
 */
@Component
public class WithdrawCommandHandler implements NonCommutativeCommandHandler<WithdrawCommand> {

    private static final Logger log = LoggerFactory.getLogger(WithdrawCommandHandler.class);
    private final WalletPeriodHelper periodHelper;

    public WithdrawCommandHandler(WalletPeriodHelper periodHelper) {
        this.periodHelper = periodHelper;
    }

    /**
     * Overrides the default handle() to perform a duplicate pre-check before business logic.
     * Must be done here, not in decide(), because business guards further down throw on retry.
     */
    @Override
    public CommandDecision handle(EventStore eventStore, WithdrawCommand command) {
        if (eventStore.exists(Query.forEventAndTag(type(WithdrawalMade.class), WITHDRAWAL_ID, command.withdrawalId()))) {
            log.debug("Duplicate withdrawal detected — returning idempotent: withdrawalId={}", command.withdrawalId());
            return CommandDecision.NoOp.empty();
        }
        return decide(eventStore, command);
    }

    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, WithdrawCommand command) {
        // Command is already validated at construction with YAVI

        var periodResult = periodHelper.projectCurrentPeriod(
                eventStore, command.walletId(), WithdrawCommand.class);
        var state = periodResult.projection().state();

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

        AppendEvent event = AppendEvent.builder(type(WithdrawalMade.class))
                .tag(WALLET_ID, command.walletId())
                .tag(WITHDRAWAL_ID, command.withdrawalId())
                .tags(periodResult.periodId().asTags())
                .data(withdrawal)
                .build();

        var periodId = periodResult.periodId();
        Query decisionModel = WalletQueryPatterns.singleWalletActivePeriodDecisionModel(
                command.walletId(), periodId.year(), periodId.month() != null ? periodId.month() : 1);

        return CommandDecision.NonCommutative.of(event, decisionModel, periodResult.projection().streamPosition());
    }
}
