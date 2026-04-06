package com.crablet.examples.notification.commands;

import com.crablet.command.IdempotentCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.examples.notification.events.WelcomeNotificationSent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
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
        implements IdempotentCommandHandler<SendWelcomeNotificationCommand> {

    private static final Logger log = LoggerFactory.getLogger(SendWelcomeNotificationCommandHandler.class);

    @Override
    public Decision decide(EventStore eventStore, SendWelcomeNotificationCommand command) {

        // Placeholder: in a real system this would call an email/SMS service
        log.info("Welcome notification → owner='{}', walletId='{}'", command.owner(), command.walletId());

        AppendEvent event = AppendEvent.builder(type(WelcomeNotificationSent.class))
                .tag(WALLET_ID, command.walletId())
                .data(WelcomeNotificationSent.of(command.walletId(), command.owner()))
                .build();

        // Idempotency: only one WelcomeNotificationSent per wallet_id
        return Decision.of(event, type(WelcomeNotificationSent.class), WALLET_ID, command.walletId());
    }
}
