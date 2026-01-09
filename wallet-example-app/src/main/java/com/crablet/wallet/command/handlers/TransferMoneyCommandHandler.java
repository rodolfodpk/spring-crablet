package com.crablet.wallet.command.handlers;

import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.commands.TransferMoneyCommand;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.exceptions.InsufficientFundsException;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.examples.wallet.period.WalletPeriodHelper.PeriodProjectionResult;
import com.crablet.examples.wallet.projections.TransferState;
import com.crablet.examples.wallet.projections.TransferStateProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.FROM_DAY;
import static com.crablet.examples.wallet.WalletTags.FROM_HOUR;
import static com.crablet.examples.wallet.WalletTags.FROM_MONTH;
import static com.crablet.examples.wallet.WalletTags.FROM_WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.FROM_YEAR;
import static com.crablet.examples.wallet.WalletTags.TO_DAY;
import static com.crablet.examples.wallet.WalletTags.TO_HOUR;
import static com.crablet.examples.wallet.WalletTags.TO_MONTH;
import static com.crablet.examples.wallet.WalletTags.TO_WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.TO_YEAR;
import static com.crablet.examples.wallet.WalletTags.TRANSFER_ID;

/**
 * Command handler for transferring money between wallets.
 * <p>
 * DCB Principle: Projects balances for both wallets + concurrency control.
 * Uses period-aware queries and ensures active periods exist for both wallets.
 */
@Component
public class TransferMoneyCommandHandler implements CommandHandler<TransferMoneyCommand> {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyCommandHandler.class);
    private final WalletPeriodHelper periodHelper;

    public TransferMoneyCommandHandler(WalletPeriodHelper periodHelper) {
        this.periodHelper = periodHelper;
    }

    @Override
    public CommandResult handle(EventStore eventStore, TransferMoneyCommand command) {
        // Command is already validated at construction with YAVI

        // Ensure active periods exist for both wallets (handled independently)
        PeriodProjectionResult fromPeriodResult = periodHelper.ensureActivePeriodAndProject(
                eventStore, command.fromWalletId(), TransferMoneyCommand.class);
        PeriodProjectionResult toPeriodResult = periodHelper.ensureActivePeriodAndProject(
                eventStore, command.toWalletId(), TransferMoneyCommand.class);

        // Project transfer state using period-aware query
        TransferProjectionResult transferProjection = projectTransferState(
                eventStore, command, fromPeriodResult, toPeriodResult);
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

        // Extract period info for tags (wallets may be in different periods)
        var fromPeriodId = fromPeriodResult.periodId();
        var toPeriodId = toPeriodResult.periodId();
        int fromYear = fromPeriodId.year();
        int fromMonth = fromPeriodId.month() != null ? fromPeriodId.month() : 1;
        Integer fromDay = fromPeriodId.day();
        Integer fromHour = fromPeriodId.hour();
        int toYear = toPeriodId.year();
        int toMonth = toPeriodId.month() != null ? toPeriodId.month() : 1;
        Integer toDay = toPeriodId.day();
        Integer toHour = toPeriodId.hour();

        AppendEvent.Builder eventBuilder = AppendEvent.builder(type(MoneyTransferred.class))
                .tag(TRANSFER_ID, command.transferId())
                .tag(FROM_WALLET_ID, command.fromWalletId())
                .tag(TO_WALLET_ID, command.toWalletId())
                .tag(FROM_YEAR, String.valueOf(fromYear))
                .tag(FROM_MONTH, String.valueOf(fromMonth))
                .tag(TO_YEAR, String.valueOf(toYear))
                .tag(TO_MONTH, String.valueOf(toMonth));
        
        if (fromDay != null) {
            eventBuilder.tag(FROM_DAY, String.valueOf(fromDay));
        }
        if (fromHour != null) {
            eventBuilder.tag(FROM_HOUR, String.valueOf(fromHour));
        }
        if (toDay != null) {
            eventBuilder.tag(TO_DAY, String.valueOf(toDay));
        }
        if (toHour != null) {
            eventBuilder.tag(TO_HOUR, String.valueOf(toHour));
        }
        
        AppendEvent event = eventBuilder.data(transfer).build();

        // Transfers are non-commutative - order matters for both wallet balances
        // DCB cursor check REQUIRED: prevents concurrent transfers causing overdrafts
        AppendCondition condition = new AppendConditionBuilder(
                transferProjection.decisionModel(), transferProjection.cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }

    /**
     * Project transfer state - balances for both wallets using period-aware queries.
     */
    private TransferProjectionResult projectTransferState(
            EventStore store,
            TransferMoneyCommand cmd,
            PeriodProjectionResult fromPeriodResult,
            PeriodProjectionResult toPeriodResult) {
        
        var fromPeriodId = fromPeriodResult.periodId();
        var toPeriodId = toPeriodResult.periodId();
        int fromYear = fromPeriodId.year();
        int fromMonth = fromPeriodId.month() != null ? fromPeriodId.month() : 1;
        int toYear = toPeriodId.year();
        int toMonth = toPeriodId.month() != null ? toPeriodId.month() : 1;

        // Use period-aware transfer decision model (combines both wallets' periods)
        Query decisionModel = WalletQueryPatterns.transferPeriodDecisionModel(
                cmd.fromWalletId(), fromYear, fromMonth,
                cmd.toWalletId(), toYear, toMonth);
        
        // Create projector instance per projection (immutable, thread-safe)
        TransferStateProjector projector = new TransferStateProjector(cmd.fromWalletId(), cmd.toWalletId());
        
        // Use EventStore's streaming projection
        com.crablet.eventstore.query.ProjectionResult<TransferState> result = 
            store.project(decisionModel, com.crablet.eventstore.store.Cursor.zero(), TransferState.class, List.of(projector));
        
        return new TransferProjectionResult(result.state(), result.cursor(), decisionModel);
    }

    private record TransferProjectionResult(
            TransferState state,
            com.crablet.eventstore.store.Cursor cursor,
            Query decisionModel
    ) {
    }
}

