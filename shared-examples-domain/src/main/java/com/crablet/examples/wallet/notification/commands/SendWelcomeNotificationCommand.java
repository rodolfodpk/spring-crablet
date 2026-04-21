package com.crablet.examples.wallet.notification.commands;

import com.crablet.examples.wallet.notification.NotificationCommand;

/**
 * Command to send a welcome notification to a newly opened wallet owner.
 * The handler logs the notification and records a {@code WelcomeNotificationSent} event.
 */
public record SendWelcomeNotificationCommand(
        String walletId,
        String owner
) implements NotificationCommand {

    public static SendWelcomeNotificationCommand of(String walletId, String owner) {
        return new SendWelcomeNotificationCommand(walletId, owner);
    }
}
