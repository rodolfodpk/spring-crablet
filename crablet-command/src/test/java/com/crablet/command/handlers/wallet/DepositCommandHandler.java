package com.crablet.command.handlers.wallet;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.examples.wallet.features.deposit.DepositCommand;
import com.crablet.eventstore.store.EventStore;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.exception.WalletNotFoundException;
import com.crablet.examples.wallet.domain.period.WalletPeriodHelper;
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
 * <p>
 * Uses period-aware queries and ensures active period exists before processing.
 */
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {

    private static final Logger log = LoggerFactory.getLogger(DepositCommandHandler.class);
    private final WalletPeriodHelper periodHelper;

    public DepositCommandHandler(WalletPeriodHelper periodHelper) {
        this.periodHelper = periodHelper;
    }

    @Override
    public CommandResult handle(EventStore eventStore, DepositCommand command) {
        // Command is already validated at construction with YAVI

        // Ensure active period exists and project balance using period-aware query
        var periodResult = periodHelper.ensureActivePeriodAndProject(
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

        // Extract period info for tags
        var periodId = periodResult.periodId();
        int year = periodId.year();
        int month = periodId.month() != null ? periodId.month() : 1;
        Integer day = periodId.day();
        Integer hour = periodId.hour();

        AppendEvent.Builder eventBuilder = AppendEvent.builder(DEPOSIT_MADE)
                .tag(WALLET_ID, command.walletId())
                .tag(DEPOSIT_ID, command.depositId())
                .tag(YEAR, String.valueOf(year))
                .tag(MONTH, String.valueOf(month));
        
        if (day != null) {
            eventBuilder.tag(DAY, String.valueOf(day));
        }
        if (hour != null) {
            eventBuilder.tag(HOUR, String.valueOf(hour));
        }
        
        AppendEvent event = eventBuilder.data(deposit).build();

        // Deposits are commutative operations - order doesn't matter
        // Balance: $100 → +$10 → +$20 = $130 (same as +$20 → +$10)
        // No DCB cursor check needed - allows parallel deposits on same wallet
        // Only requirement: wallet must exist (validated above)
        // Idempotency via deposit_id tag prevents duplicates
        AppendCondition condition = AppendCondition.empty();

        return CommandResult.of(List.of(event), condition);
    }
}
