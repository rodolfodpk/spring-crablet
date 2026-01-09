package com.crablet.command.handlers.wallet;

import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.EventStore;
import com.crablet.examples.wallet.WalletQueryPatterns;
import com.crablet.examples.wallet.commands.WithdrawCommand;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.examples.wallet.exceptions.InsufficientFundsException;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.DAY;
import static com.crablet.examples.wallet.WalletTags.HOUR;
import static com.crablet.examples.wallet.WalletTags.MONTH;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.WITHDRAWAL_ID;
import static com.crablet.examples.wallet.WalletTags.YEAR;

/**
 * Command handler for withdrawing money from wallets.
 * <p>
 * DCB Principle: Projects only wallet balance + existence - minimal state needed.
 * Does not project full WalletState since only balance and existence are required.
 * <p>
 * Uses period-aware queries and ensures active period exists before processing.
 */
@Component
public class WithdrawCommandHandler implements CommandHandler<WithdrawCommand> {

    private static final Logger log = LoggerFactory.getLogger(WithdrawCommandHandler.class);
    private final WalletPeriodHelper periodHelper;

    public WithdrawCommandHandler(WalletPeriodHelper periodHelper) {
        this.periodHelper = periodHelper;
    }

    @Override
    public CommandResult handle(EventStore eventStore, WithdrawCommand command) {
        // Command is already validated at construction with YAVI

        // Ensure active period exists and project balance using period-aware query
        var periodResult = periodHelper.ensureActivePeriodAndProject(
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

        // Extract period info for tags
        var periodId = periodResult.periodId();
        int year = periodId.year();
        int month = periodId.month() != null ? periodId.month() : 1;
        Integer day = periodId.day();
        Integer hour = periodId.hour();

        AppendEvent.Builder eventBuilder = AppendEvent.builder(type(WithdrawalMade.class))
                .tag(WALLET_ID, command.walletId())
                .tag(WITHDRAWAL_ID, command.withdrawalId())
                .tag(YEAR, String.valueOf(year))
                .tag(MONTH, String.valueOf(month));
        
        if (day != null) {
            eventBuilder.tag(DAY, String.valueOf(day));
        }
        if (hour != null) {
            eventBuilder.tag(HOUR, String.valueOf(hour));
        }
        
        AppendEvent event = eventBuilder.data(withdrawal).build();

        // Use period-aware decision model query for DCB concurrency control
        Query decisionModel = WalletQueryPatterns.singleWalletActivePeriodDecisionModel(
                command.walletId(), year, month);

        // Withdrawals are non-commutative - order matters for balance validation
        // DCB cursor check REQUIRED: prevents concurrent withdrawals exceeding balance
        // Example: $100 balance, two $80 withdrawals - both see $100, but only one should succeed
        AppendCondition condition = new AppendConditionBuilder(decisionModel, periodResult.projection().cursor())
                .build();

        return CommandResult.of(List.of(event), condition);
    }
}
