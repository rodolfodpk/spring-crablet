package com.crablet.eventstore.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for optional event store wakeup notifications.
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
