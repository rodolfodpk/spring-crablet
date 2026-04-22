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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated PostgreSQL LISTEN/NOTIFY wakeup source.
 */
public final class PostgresNotifyWakeupSource implements ProcessorWakeupSource {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyWakeupSource.class);

    private final String jdbcUrl;
    private final @Nullable String username;
    private final @Nullable String password;
    private final String channel;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private @Nullable Thread listenerThread;
    private @Nullable Connection connection;

    public PostgresNotifyWakeupSource(
            String jdbcUrl, @Nullable String username, @Nullable String password, String channel) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.channel = validateChannel(channel);
    }

    @Override
    public synchronized void start(Runnable onWakeup) {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        listenerThread = new Thread(() -> listenLoop(onWakeup), "crablet-pg-listen-" + channel);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @Override
    public synchronized void close() {
        running.set(false);
        closeConnectionQuietly();
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    private void listenLoop(Runnable onWakeup) {
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
                if (notifications == null) {
                    continue;
                }
                for (PGNotification ignored : notifications) {
                    if (!running.get()) {
                        break;
                    }
                    onWakeup.run();
                }
            }
        } catch (SQLException e) {
            if (running.get()) {
                log.warn("Postgres wakeup listener stopped for channel {}: {}", channel, e.getMessage());
            }
        } finally {
            closeConnectionQuietly();
        }
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
