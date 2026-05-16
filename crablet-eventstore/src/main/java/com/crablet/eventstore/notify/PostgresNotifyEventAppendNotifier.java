package com.crablet.eventstore.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Best-effort PostgreSQL notifier backed by {@code pg_notify}.
 *
 * <h2>Failure hygiene (Phase E)</h2>
 * <p>After {@value #FAILURE_THRESHOLD} consecutive failures the notifier enters a
 * {@value #COOLDOWN_SECONDS}-second cooldown and suppresses further log noise. During
 * the cooldown, notify calls are skipped silently — the scheduled polling backstop
 * ensures correctness. The failure counter and cooldown reset on the first successful
 * notify after recovery.
 */
public final class PostgresNotifyEventAppendNotifier implements EventAppendNotifier {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyEventAppendNotifier.class);
    private static final String PG_NOTIFY_SQL = "SELECT pg_notify(?, ?)";

    private static final int  FAILURE_THRESHOLD  = 5;
    private static final long COOLDOWN_SECONDS   = 30L;
    private static final long COOLDOWN_NANOS     = TimeUnit.SECONDS.toNanos(COOLDOWN_SECONDS);

    private final DataSource dataSource;
    private final String channel;
    private final String payload;

    // Failure tracking — written and read on the thread(s) calling notifyEventsAppended().
    // AtomicInteger for the counter; volatile long for the cooldown timestamp so that
    // concurrent callers see the suppression flag without additional locking.
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long suppressUntilNanos = 0L;

    public PostgresNotifyEventAppendNotifier(DataSource dataSource, String channel, String payload) {
        this.dataSource = dataSource;
        this.channel = channel;
        this.payload = payload;
    }

    @Override
    public void notifyEventsAppended() {
        sendNotify(payload);
    }

    @Override
    public void notifyEventsAppended(Set<String> eventTypes) {
        String encodedPayload;
        if (eventTypes.isEmpty()) {
            encodedPayload = "*";
        } else {
            String joined = String.join(",", eventTypes);
            // pg_notify payload is limited to 8000 bytes; fall back to wildcard if exceeded
            encodedPayload = joined.length() <= 7900 ? joined : "*";
        }
        sendNotify(encodedPayload);
    }

    private void sendNotify(String notifyPayload) {
        // Skip silently while in cooldown — polling provides the correctness backstop
        if (System.nanoTime() < suppressUntilNanos) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PG_NOTIFY_SQL)) {
            statement.setString(1, channel);
            statement.setString(2, notifyPayload);
            statement.execute();

            int prev = consecutiveFailures.getAndSet(0);
            if (prev > 0) {
                log.info("pg_notify recovered on channel '{}' after {} consecutive failure(s)", channel, prev);
                suppressUntilNanos = 0L;
            }
        } catch (SQLException e) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= FAILURE_THRESHOLD) {
                suppressUntilNanos = System.nanoTime() + COOLDOWN_NANOS;
                log.warn("pg_notify failing repeatedly on channel '{}' ({}x consecutive), "
                        + "suppressing notifications for {}s. Scheduled polling remains active. "
                        + "Last error: {}",
                        channel, failures, COOLDOWN_SECONDS, e.getMessage());
            } else {
                log.warn("Failed to publish pg_notify on channel '{}': {}", channel, e.getMessage());
            }
        }
    }
}
