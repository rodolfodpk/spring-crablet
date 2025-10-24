package com.wallets.features.openwallet;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendConditionBuilder;
import com.crablet.core.AppendEvent;
import com.crablet.core.CommandHandler;
import com.crablet.core.CommandResult;
import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
import com.crablet.core.Query;
import com.wallets.domain.WalletEventTypes;
import com.wallets.domain.WalletTags;
import com.wallets.domain.event.WalletOpened;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Command handler for opening wallets.
 * <p>
 * DCB Principle: Projects only wallet existence (boolean) - minimal state needed.
 * Does not project full WalletState since only existence check is required.
 */
@Component
public class OpenWalletCommandHandler implements CommandHandler<OpenWalletCommand> {

    private static final Logger log = LoggerFactory.getLogger(OpenWalletCommandHandler.class);

    public OpenWalletCommandHandler() {
    }

    @Override
    public CommandResult handle(EventStore eventStore, OpenWalletCommand command) {
        // Command is already validated at construction with YAVI

        // 2. DCB: NO validation query needed!
        //    AppendCondition will enforce uniqueness atomically

        // 3. Create event (optimistic - assume wallet doesn't exist)
        WalletOpened walletOpened = WalletOpened.of(
                command.walletId(),
                command.owner(),
                command.initialBalance()
        );

        AppendEvent event = AppendEvent.builder(WalletEventTypes.WALLET_OPENED)
                .tag(WalletTags.WALLET_ID, command.walletId())
                .data(walletOpened)
                .build();

        // 4. Build condition to enforce uniqueness using DCB idempotency pattern
        //    Fails if ANY WalletOpened event exists for this wallet_id (idempotency check)
        //    No concurrency check needed for wallet creation - only idempotency matters
        AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                .withIdempotencyCheck(WalletEventTypes.WALLET_OPENED, WalletTags.WALLET_ID, command.walletId())
                .build();

        // 5. Return - appendIf will:
        //    - Check condition atomically
        //    - Throw ConcurrencyException if wallet exists
        //    - Append event if wallet doesn't exist
        return CommandResult.of(List.of(event), condition);
    }


    @Override
    public String getCommandType() {
        return "open_wallet";
    }

}
