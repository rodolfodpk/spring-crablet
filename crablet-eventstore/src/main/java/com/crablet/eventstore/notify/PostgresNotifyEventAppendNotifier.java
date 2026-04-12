package com.crablet.eventstore.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Best-effort PostgreSQL notifier backed by {@code pg_notify}.
 */
public final class PostgresNotifyEventAppendNotifier implements EventAppendNotifier {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyEventAppendNotifier.class);
    private static final String PG_NOTIFY_SQL = "SELECT pg_notify(?, ?)";

    private final DataSource dataSource;
    private final String channel;
    private final String payload;

    public PostgresNotifyEventAppendNotifier(DataSource dataSource, String channel, String payload) {
        this.dataSource = dataSource;
        this.channel = channel;
        this.payload = payload;
    }

    @Override
    public void notifyEventsAppended() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PG_NOTIFY_SQL)) {
            statement.setString(1, channel);
            statement.setString(2, payload);
            statement.execute();
        } catch (SQLException e) {
            log.warn("Failed to publish pg_notify on channel {}: {}", channel, e.getMessage());
        }
    }
}
