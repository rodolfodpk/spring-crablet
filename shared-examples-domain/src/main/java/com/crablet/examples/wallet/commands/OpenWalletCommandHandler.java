package com.crablet.examples.wallet.commands;

import com.crablet.command.IdempotentCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.examples.wallet.events.WalletOpened;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;

/**
 * Command handler for opening wallets.
 * <p>
 * DCB Principle: Uses idempotency check — wallet creation must succeed exactly once per wallet_id.
 * No state projection is needed; the idempotency condition enforces uniqueness atomically.
 */
@Component
public class OpenWalletCommandHandler implements IdempotentCommandHandler<OpenWalletCommand> {

    public OpenWalletCommandHandler() {
    }

    @Override
    public Decision decide(EventStore eventStore, OpenWalletCommand command) {
        // Command is already validated at construction with YAVI

        WalletOpened walletOpened = WalletOpened.of(
                command.walletId(),
                command.owner(),
                command.initialBalance()
        );

        AppendEvent event = AppendEvent.builder(type(WalletOpened.class))
                .tag(WALLET_ID, command.walletId())
                .data(walletOpened)
                .build();

        // Idempotency: fails if ANY WalletOpened event already exists for this wallet_id
        return new Decision(List.of(event), type(WalletOpened.class), WALLET_ID, command.walletId());
    }
}
