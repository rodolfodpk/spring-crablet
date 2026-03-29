package com.crablet.examples.notification.commands;

import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.EventStore;
import com.crablet.examples.notification.events.WelcomeNotificationSent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.notification.NotificationTags.WALLET_ID;

/**
 * Handles {@link SendWelcomeNotificationCommand}.
 *
 * <p>Logs the notification (placeholder for real delivery) and appends a
 * {@link WelcomeNotificationSent} event. Idempotency is enforced via DCB:
 * only one welcome notification is ever recorded per wallet.
 */
@Component
public class SendWelcomeNotificationCommandHandler
        implements CommandHandler<SendWelcomeNotificationCommand> {

    private static final Logger log = LoggerFactory.getLogger(SendWelcomeNotificationCommandHandler.class);

    @Override
    public CommandResult handle(EventStore eventStore, SendWelcomeNotificationCommand command) {

        // Placeholder: in a real system this would call an email/SMS service
        log.info("Welcome notification → owner='{}', walletId='{}'", command.owner(), command.walletId());

        AppendEvent event = AppendEvent.builder(type(WelcomeNotificationSent.class))
                .tag(WALLET_ID, command.walletId())
                .data(WelcomeNotificationSent.of(command.walletId(), command.owner()))
                .build();

        // Idempotency: only one WelcomeNotificationSent per wallet_id
        AppendCondition condition = new AppendConditionBuilder(Query.empty(), Cursor.zero())
                .withIdempotencyCheck(type(WelcomeNotificationSent.class), WALLET_ID, command.walletId())
                .build();

        return CommandResult.of(List.of(event), condition);
    }
}
