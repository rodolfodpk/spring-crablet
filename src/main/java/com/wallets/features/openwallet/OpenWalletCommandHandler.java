package com.wallets.features.openwallet;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.CommandHandler;
import com.crablet.core.CommandResult;
import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
import com.crablet.core.Query;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.WalletQueryPatterns;
import com.wallets.domain.event.WalletOpened;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.core.CommandHandler.serializeEvent;

/**
 * Command handler for opening wallets.
 * <p>
 * DCB Principle: Projects only wallet existence (boolean) - minimal state needed.
 * Does not project full WalletState since only existence check is required.
 */
@Component
public class OpenWalletCommandHandler implements CommandHandler<OpenWalletCommand> {

    private static final Logger log = LoggerFactory.getLogger(OpenWalletCommandHandler.class);

    private final ObjectMapper objectMapper;

    public OpenWalletCommandHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

        String jsonData = serializeEvent(objectMapper, walletOpened);
        AppendEvent event = AppendEvent.of(
                "WalletOpened",
                List.of(new Tag("wallet_id", command.walletId())),
                jsonData.getBytes()
        );

        // 4. Build condition to enforce uniqueness using domain pattern
        //    Fails if ANY WalletOpened event exists for this wallet_id
        Query existenceQuery = WalletQueryPatterns.walletExistenceQuery(command.walletId());
        AppendCondition condition = existenceQuery
                .toAppendCondition(Cursor.zero()) // Expect empty stream for new wallet
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
