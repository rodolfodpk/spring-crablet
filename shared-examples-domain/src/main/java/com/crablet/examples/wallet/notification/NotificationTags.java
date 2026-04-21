package com.crablet.examples.wallet.notification;

/**
 * Tag name constants for the notification domain.
 */
public final class NotificationTags {

    private NotificationTags() {}

    /**
     * Tag for the wallet ID associated with a notification.
     * Used as idempotency key to ensure one welcome notification per wallet.
     */
    public static final String WALLET_ID = "wallet_id";
}
