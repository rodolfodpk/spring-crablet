package com.crablet.eventstore.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for PostgreSQL NOTIFY on event appends.
 *
 * <p>After every successful append the event store calls {@code pg_notify(channel, payload)}
 * inside the append SQL function. If no one is LISTENing, Postgres silently discards
 * the notification.
 *
 * <p>Tune only when you need to separate channels between environments.
 *
 * <pre>{@code
 * # optional - this is the default
 * crablet.eventstore.notifications.channel=crablet_events
 * }</pre>
 */
@ConfigurationProperties(prefix = "crablet.eventstore.notifications")
public class EventStoreNotificationProperties {

    private String channel = "crablet_events";

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        if (channel == null || !channel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL notification channel: " + channel);
        }
        this.channel = channel;
    }
}
