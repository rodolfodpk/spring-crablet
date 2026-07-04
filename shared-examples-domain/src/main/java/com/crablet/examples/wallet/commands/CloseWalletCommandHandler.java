package com.crablet.examples.wallet.commands;

import com.crablet.command.CommandDecision;
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.AppendCondition;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.events.WalletClosed;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import com.crablet.examples.wallet.projections.WalletBalanceState;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;

/**
 * Command handler for permanently closing a wallet.
 * <p>
 * Uses a NonCommutative append so the DCB check catches any concurrent lifecycle
 * change (another close) that arrives between this handler's projection and its append.
 * <p>
 * Projects only the lifecycle model — {@code WalletOpened} and {@code WalletClosed} —
 * which is sufficient to determine existence without reading the full transaction history.
 * Deposit and withdraw handlers already guard against a closed wallet via the
 * lifecycle guard in {@code WalletQueryPatterns.walletLifecycleModel()}.
 */
@Component
public class CloseWalletCommandHandler implements NonCommutativeCommandHandler<CloseWalletCommand> {

    private final ClockProvider clockProvider;

    public CloseWalletCommandHandler(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, CloseWalletCommand command) {
        Query decisionModel = WalletQueryPatterns.walletLifecycleModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                decisionModel, new WalletBalanceStateProjector());

        if (!projection.state().isExisting()) {
            throw new WalletNotFoundException(command.walletId());
        }

        AppendEvent event = AppendEvent.of(
                type(WalletClosed.class), WALLET_ID, command.walletId(),
                new WalletClosed(command.walletId(), clockProvider.now()));

        return CommandDecision.NonCommutative.of(event,
                AppendCondition.failIfChanged(decisionModel).after(projection.streamPosition()));
    }
}
