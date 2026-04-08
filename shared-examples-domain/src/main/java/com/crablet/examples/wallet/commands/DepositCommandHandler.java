package com.crablet.examples.wallet.commands;

import com.crablet.command.CommandDecision;
import com.crablet.command.CommutativeCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.wallet.WalletTags.DEPOSIT_ID;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;

/**
 * Command handler for depositing money into wallets.
 * <p>
 * DCB Principle: Commutative operation — deposit order does not affect correctness.
 * No stream position check needed; allows parallel deposits on the same wallet.
 * Idempotency is provided by the deposit_id tag.
 */
@Component
public class DepositCommandHandler implements CommutativeCommandHandler<DepositCommand> {

    private static final Logger log = LoggerFactory.getLogger(DepositCommandHandler.class);
    private final WalletPeriodHelper periodHelper;

    public DepositCommandHandler(WalletPeriodHelper periodHelper) {
        this.periodHelper = periodHelper;
    }

    @Override
    public CommandDecision.Commutative decide(EventStore eventStore, DepositCommand command) {
        // Command is already validated at construction with YAVI

        var periodResult = periodHelper.projectCurrentPeriod(
                eventStore, command.walletId(), DepositCommand.class);
        var state = periodResult.projection().state();

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

        AppendEvent event = AppendEvent.builder(type(DepositMade.class))
                .tag(WALLET_ID, command.walletId())
                .tag(DEPOSIT_ID, command.depositId())
                .tags(periodResult.periodId().asTags())
                .data(deposit)
                .build();

        // Lifecycle guard: detect if wallet state changed (e.g., WalletClosed) between projection
        // and append, without blocking concurrent deposits (DepositMade is not in the guard query).
        Query lifecycleGuard = WalletQueryPatterns.walletLifecycleModel(command.walletId());
        return CommandDecision.Commutative.of(event, lifecycleGuard, periodResult.projection().streamPosition());
    }
}
