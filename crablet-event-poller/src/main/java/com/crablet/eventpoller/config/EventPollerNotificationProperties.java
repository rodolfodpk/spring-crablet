package com.crablet.eventpoller.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
    private @org.jspecify.annotations.Nullable String jdbcUrl;
    private @org.jspecify.annotations.Nullable String username;
    private @org.jspecify.annotations.Nullable String password;

    /**
     * Debounce window for cross-read wakeup coalescing.
     *
     * <p>When the Postgres driver splits a burst across multiple {@code getNotifications()}
     * reads, each read would otherwise trigger a full fan-out to all processors. A debounce
     * window accumulates back-to-back reads and delivers one merged wakeup per window.
     *
     * <p>Set to {@code 0ms} to disable cross-read coalescing (one wakeup per driver read).
     * Values between 10–50 ms are typical; raise if scheduler churn shows in metrics,
     * lower if sub-10 ms NOTIFY latency matters more than churn reduction.
     *
     * <p>Accepts Spring {@link Duration} syntax: {@code 20ms}, {@code 1s}, {@code PT0.05S}.
     */
    private Duration debounce = Duration.ofMillis(20);

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public @Nullable String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Duration getDebounce() {
        return debounce;
    }

    public void setDebounce(Duration debounce) {
        this.debounce = debounce;
    }
}
