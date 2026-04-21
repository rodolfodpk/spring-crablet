package com.crablet.examples.wallet.notification.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all notification-related events.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WelcomeNotificationSent.class, name = "WelcomeNotificationSent")
})
public sealed interface NotificationEvent permits WelcomeNotificationSent {
}
