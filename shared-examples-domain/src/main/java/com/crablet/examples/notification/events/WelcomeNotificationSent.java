package com.crablet.examples.notification.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Event recording that a welcome notification was sent for a newly opened wallet.
 */
public record WelcomeNotificationSent(
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("owner") String owner,
        @JsonProperty("sent_at") Instant sentAt
) implements NotificationEvent {

    public static WelcomeNotificationSent of(String walletId, String owner) {
        return new WelcomeNotificationSent(walletId, owner, Instant.now());
    }
}
