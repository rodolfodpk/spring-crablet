package com.crablet.examples.wallet.commands;

import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.examples.wallet.WalletQueryPatterns;
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

import static com.crablet.eventstore.EventType.type;
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
 * DCB Principle: Non-commutative operation — order matters for both wallet balances.
 * Cursor-based DCB check prevents concurrent transfers from causing overdrafts.
 */
@Component
public class TransferMoneyCommandHandler implements NonCommutativeCommandHandler<TransferMoneyCommand> {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyCommandHandler.class);
    private final WalletPeriodHelper periodHelper;

    public TransferMoneyCommandHandler(WalletPeriodHelper periodHelper) {
        this.periodHelper = periodHelper;
    }

    @Override
    public Decision decide(EventStore eventStore, TransferMoneyCommand command) {
        // Command is already validated at construction with YAVI

        PeriodProjectionResult fromPeriodResult = periodHelper.projectCurrentPeriod(
                eventStore, command.fromWalletId(), TransferMoneyCommand.class);
        PeriodProjectionResult toPeriodResult = periodHelper.projectCurrentPeriod(
                eventStore, command.toWalletId(), TransferMoneyCommand.class);

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

        int fromNewBalance = transferState.fromWallet().balance() - command.amount();
        int toNewBalance = transferState.toWallet().balance() + command.amount();

        MoneyTransferred transfer = MoneyTransferred.of(
                command.transferId(),
                command.fromWalletId(),
                command.toWalletId(),
                command.amount(),
                fromNewBalance,
                toNewBalance,
                command.description()
        );

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
                .tag(FROM_YEAR, fromYear)
                .tag(FROM_MONTH, fromMonth)
                .tag(TO_YEAR, toYear)
                .tag(TO_MONTH, toMonth);

        if (fromDay != null)  eventBuilder.tag(FROM_DAY, fromDay);
        if (fromHour != null) eventBuilder.tag(FROM_HOUR, fromHour);
        if (toDay != null)    eventBuilder.tag(TO_DAY, toDay);
        if (toHour != null)   eventBuilder.tag(TO_HOUR, toHour);

        AppendEvent event = eventBuilder.data(transfer).build();

        return new Decision(List.of(event), transferProjection.decisionModel(), transferProjection.streamPosition());
    }

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

        Query decisionModel = WalletQueryPatterns.transferPeriodDecisionModel(
                cmd.fromWalletId(), fromYear, fromMonth,
                cmd.toWalletId(), toYear, toMonth);

        TransferStateProjector projector = new TransferStateProjector(cmd.fromWalletId(), cmd.toWalletId());

        com.crablet.eventstore.query.ProjectionResult<TransferState> result =
            store.project(decisionModel, com.crablet.eventstore.StreamPosition.zero(), TransferState.class, List.of(projector));

        return new TransferProjectionResult(result.state(), result.streamPosition(), decisionModel);
    }

    private record TransferProjectionResult(
            TransferState state,
            com.crablet.eventstore.StreamPosition streamPosition,
            Query decisionModel
    ) {}
}
