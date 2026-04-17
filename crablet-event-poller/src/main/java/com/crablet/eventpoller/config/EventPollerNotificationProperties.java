package com.crablet.eventpoller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the PostgreSQL LISTEN wakeup source.
 *
 * <p>When {@code jdbc-url} is set the poller opens a dedicated persistent connection and
 * issues {@code LISTEN <channel>}. Each NOTIFY from the event store cancels the current
 * scheduled delay and triggers an immediate poll cycle, reducing end-to-end latency to
 * milliseconds instead of the full polling interval.
 *
 * <p>When {@code jdbc-url} is absent the poller falls back to pure scheduled polling —
 * no configuration change is needed to opt out.
 *
 * <h2>Connection pooler / proxy compatibility</h2>
 * <p>The NOTIFY side (event store) is a plain SQL call and works through any pooler.
 * The LISTEN side requires a <strong>direct, persistent connection</strong>:
 *
 * <ul>
 *   <li><b>PgBouncer session mode</b> — supported</li>
 *   <li><b>PgBouncer transaction mode</b> — not supported; LISTEN requires session state</li>
 *   <li><b>PgCat</b> — not supported in transaction mode for the same reason</li>
 *   <li><b>Aurora PostgreSQL (direct)</b> — supported</li>
 *   <li><b>Aurora via RDS Proxy</b> — not supported; RDS Proxy uses transaction-mode pooling</li>
 * </ul>
 *
 * <p>Always point {@code jdbc-url} at the database directly, bypassing any pooler:
 *
 * <pre>{@code
 * crablet.event-poller.notifications.jdbc-url=jdbc:postgresql://db-host:5432/mydb
 * crablet.event-poller.notifications.username=app_user
 * crablet.event-poller.notifications.password=secret
 * # optional — must match crablet.eventstore.notifications.channel
 * crablet.event-poller.notifications.channel=crablet_events
 * }</pre>
 *
 * <p>When wakeup is active you can safely raise the polling interval to 30 s or more;
 * scheduled polling becomes a safety net rather than the primary latency mechanism.
 */
@ConfigurationProperties(prefix = "crablet.event-poller.notifications")
public class EventPollerNotificationProperties {

    private String channel = "crablet_events";
    private String jdbcUrl;
    private String username;
    private String password;

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
