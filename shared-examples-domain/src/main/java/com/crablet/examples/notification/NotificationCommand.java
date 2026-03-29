package com.crablet.examples.notification;

import com.crablet.examples.notification.commands.SendWelcomeNotificationCommand;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface for all notification-related commands.
 * Uses Jackson polymorphic serialization so CommandExecutor can extract the commandType.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SendWelcomeNotificationCommand.class, name = "send_welcome_notification")
})
public interface NotificationCommand {
}
