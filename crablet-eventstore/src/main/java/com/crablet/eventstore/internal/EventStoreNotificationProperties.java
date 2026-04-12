package com.crablet.eventstore.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for PostgreSQL NOTIFY on event appends.
 *
 * <p>After every successful append the event store calls {@code pg_notify(channel, payload)}
 * on the write datasource. If no one is LISTENing, Postgres silently discards the
 * notification — the cost is negligible and no configuration is required to opt out.
 *
 * <p>Tune only when you need to separate channels between environments or customise
 * the payload token that pollers key on.
 *
 * <pre>{@code
 * # optional — these are the defaults
 * crablet.eventstore.notifications.channel=crablet_events
 * crablet.eventstore.notifications.payload=events-appended
 * }</pre>
 */
@ConfigurationProperties(prefix = "crablet.eventstore.notifications")
public class EventStoreNotificationProperties {

    private String channel = "crablet_events";
    private String payload = "events-appended";

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
