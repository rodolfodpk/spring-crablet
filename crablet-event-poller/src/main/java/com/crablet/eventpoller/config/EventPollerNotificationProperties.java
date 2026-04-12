package com.crablet.eventpoller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for optional poller wakeup notifications.
 */
@ConfigurationProperties(prefix = "crablet.event-poller.notifications")
public class EventPollerNotificationProperties {

    private boolean enabled = false;
    private String channel = "crablet_events";
    private String jdbcUrl;
    private String username;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
