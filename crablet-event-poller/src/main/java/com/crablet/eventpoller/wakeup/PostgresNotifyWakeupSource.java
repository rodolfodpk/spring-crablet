package com.crablet.eventpoller.wakeup;

import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Dedicated PostgreSQL LISTEN/NOTIFY wakeup source.
 *
 * <p>Shared across modules: multiple callers may register via
 * {@link #start(Set, Runnable)}. The LISTEN connection and listener thread are
 * started on the first registration and stopped when the last subscriber
 * unregisters via {@link #close(Runnable)}. {@link #close()} force-closes
 * regardless of remaining subscribers (Spring context shutdown).
 *
 * <p>Each subscriber declares the set of event type names it is interested in.
 * When a notification arrives with a type-encoded payload, only subscribers
 * whose declared types intersect the payload types are woken. A wildcard payload
 * ({@code "*"}, blank, or {@code null}) wakes all subscribers. An empty declared
 * type set means the subscriber wants all event types.
 */
public final class PostgresNotifyWakeupSource implements ProcessorWakeupSource {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyWakeupSource.class);

    private final String jdbcUrl;
    private final @Nullable String username;
    private final @Nullable String password;
    private final String channel;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    private @Nullable Thread listenerThread;
    private @Nullable Connection connection;

    private record Subscriber(Set<String> eventTypes, Runnable onWakeup) {}

    public PostgresNotifyWakeupSource(
            String jdbcUrl, @Nullable String username, @Nullable String password, String channel) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.channel = validateChannel(channel);
    }

    @Override
    public synchronized void start(Runnable onWakeup) {
        start(Set.of(), onWakeup);
    }

    @Override
    public synchronized void start(Set<String> subscribedEventTypes, Runnable onWakeup) {
        subscribers.add(new Subscriber(subscribedEventTypes, onWakeup));
        if (running.compareAndSet(false, true)) {
            listenerThread = new Thread(this::listenLoop, "crablet-pg-listen-" + channel);
            listenerThread.setDaemon(true);
            listenerThread.start();
        }
    }

    @Override
    public synchronized void close(Runnable onWakeup) {
        subscribers.removeIf(s -> s.onWakeup() == onWakeup);
        if (subscribers.isEmpty()) {
            stopListener();
        }
    }

    @Override
    public synchronized void close() {
        subscribers.clear();
        stopListener();
    }

    private void stopListener() {
        running.set(false);
        closeConnectionQuietly();
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    private void listenLoop() {
        try {
            Connection listenConnection = username == null && password == null
                    ? DriverManager.getConnection(jdbcUrl)
                    : DriverManager.getConnection(jdbcUrl, username, password);
            connection = listenConnection;
            try (Statement statement = listenConnection.createStatement()) {
                statement.execute("LISTEN " + channel);
            }

            PGConnection pgConnection = listenConnection.unwrap(PGConnection.class);
            while (running.get()) {
                PGNotification[] notifications = pgConnection.getNotifications(1000);
                if (notifications == null || notifications.length == 0) {
                    continue;
                }
                if (!running.get()) {
                    break;
                }
                dispatchBatch(notifications);
            }
        } catch (SQLException e) {
            if (running.get()) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unwrap")) {
                    log.warn("LISTEN wakeup disabled for channel '{}': could not obtain a direct PostgreSQL connection. "
                            + "Check that notifications.jdbc-url points directly at PostgreSQL and not through a pooler "
                            + "(PgBouncer transaction mode, PgCat, RDS Proxy). Falling back to scheduled polling. "
                            + "Cause: {}", channel, e.getMessage());
                } else {
                    log.warn("LISTEN wakeup listener stopped for channel '{}': {}. "
                            + "Falling back to scheduled polling.", channel, e.getMessage());
                }
            }
        } finally {
            closeConnectionQuietly();
        }
    }

    /**
     * Aggregate all payloads in the batch, then wake each subscriber at most once.
     *
     * <p>If any notification carries a wildcard payload, the whole batch is treated
     * as a wildcard and all subscribers are woken. Otherwise the event types from all
     * notifications are unioned and each subscriber is woken only if its declared types
     * intersect the union (or if it subscribes to all types via an empty set).
     */
    private void dispatchBatch(PGNotification[] notifications) {
        boolean hasWildcard = false;
        Set<String> batchTypes = new HashSet<>();
        for (PGNotification n : notifications) {
            Set<String> parsed = parsePayload(n.getParameter());
            if (parsed.isEmpty()) {
                hasWildcard = true;
                break;
            }
            batchTypes.addAll(parsed);
        }

        for (Subscriber sub : subscribers) {
            boolean wake = sub.eventTypes().isEmpty()                            // subscriber wants all types
                    || hasWildcard                                               // sender does not know types
                    || !Collections.disjoint(sub.eventTypes(), batchTypes);     // type intersection
            if (wake) {
                sub.onWakeup().run();
            }
        }
    }

    /**
     * Parse a pg_notify payload into a set of event type names.
     * Returns an empty set to signal "wildcard" (wake all subscribers).
     */
    static Set<String> parsePayload(@Nullable String payload) {
        if (payload == null || payload.isBlank() || "*".equals(payload.trim())) {
            return Set.of(); // empty = wildcard
        }
        return Arrays.stream(payload.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private void closeConnectionQuietly() {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.debug("Failed to close Postgres wakeup listener connection: {}", e.getMessage());
        } finally {
            connection = null;
        }
    }

    private static String validateChannel(String channel) {
        if (channel == null || !channel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL notification channel: " + channel);
        }
        return channel;
    }
}
