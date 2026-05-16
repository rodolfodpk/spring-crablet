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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Dedicated PostgreSQL LISTEN/NOTIFY wakeup source.
 *
 * <p>Shared across modules: multiple callers register via {@link #start(Set, Runnable)}.
 * The LISTEN connection and listener thread start on the first registration and stop when
 * the last subscriber unregisters via {@link #close(Runnable)}.
 * {@link #close()} force-closes regardless of remaining subscribers (context shutdown).
 *
 * <h2>Batch coalescing (Phase A)</h2>
 * <p>Multiple notifications returned in a single {@code getNotifications()} call are merged
 * into one wakeup decision: event types are unioned; any wildcard payload makes the whole
 * batch a wildcard. Each subscriber is called at most once per driver read.
 *
 * <h2>Cross-read debounce (Phase B)</h2>
 * <p>When {@code debounceMs > 0}, back-to-back driver reads within the window are further
 * merged so that a Postgres burst split across several reads still results in one wakeup
 * per subscriber per window. {@code debounceMs = 0} disables this and dispatches
 * immediately after each read (Phase A only).
 *
 * <h2>Event-type filtering (Phase D)</h2>
 * <p>Each subscriber declares its interested event types. Wildcard subscribers (empty set)
 * are always woken. Typed subscribers are woken only when the accumulated batch intersects
 * their declared types, or when the batch carries a wildcard payload.
 */
@SuppressWarnings("NullAway") // AtomicReference<BatchState> uses null as "no pending state" sentinel
public final class PostgresNotifyWakeupSource implements ProcessorWakeupSource {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyWakeupSource.class);

    private final String jdbcUrl;
    private final @Nullable String username;
    private final @Nullable String password;
    private final String channel;
    private final long debounceMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    // Cross-read debounce state
    private final AtomicReference<BatchState> accumulator = new AtomicReference<>(null);
    private final AtomicBoolean pendingFlush = new AtomicBoolean(false);
    private final ScheduledExecutorService flushScheduler;

    private @Nullable Thread listenerThread;
    private @Nullable Connection connection;

    private record Subscriber(Set<String> eventTypes, Runnable onWakeup) {}

    /**
     * Immutable accumulated batch state. Merges multiple driver reads.
     * {@code wildcard = true} means "wake all subscribers regardless of types."
     */
    record BatchState(boolean wildcard, Set<String> types) {
        static BatchState ofBatch(boolean wildcard, Set<String> types) {
            return new BatchState(wildcard, wildcard ? Set.of() : Set.copyOf(types));
        }

        BatchState merge(BatchState other) {
            if (this.wildcard || other.wildcard) return new BatchState(true, Set.of());
            Set<String> merged = new HashSet<>(types);
            merged.addAll(other.types);
            return new BatchState(false, Collections.unmodifiableSet(merged));
        }
    }

    public PostgresNotifyWakeupSource(
            String jdbcUrl, @Nullable String username, @Nullable String password, String channel) {
        this(jdbcUrl, username, password, channel, 20L);
    }

    public PostgresNotifyWakeupSource(
            String jdbcUrl, @Nullable String username, @Nullable String password,
            String channel, long debounceMs) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.channel = validateChannel(channel);
        this.debounceMs = debounceMs;
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crablet-pg-notify-flush-" + channel);
            t.setDaemon(true);
            return t;
        });
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
            drainAndStop();
        }
    }

    @Override
    public synchronized void close() {
        drainAndStop();
        subscribers.clear();
    }

    private void drainAndStop() {
        // Cancel any pending scheduled flush and drain its accumulated state immediately,
        // so subscribers receive one final wakeup before the connection closes.
        flushScheduler.shutdownNow();
        pendingFlush.set(false);
        BatchState pending = accumulator.getAndSet(null);
        if (pending != null) {
            dispatchToSubscribers(pending);
        }
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
     * Merge one driver read into a batch state and either dispatch immediately
     * (debounceMs == 0) or accumulate for the next flush window.
     */
    void dispatchBatch(PGNotification[] notifications) {
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
        BatchState incoming = BatchState.ofBatch(hasWildcard, batchTypes);

        if (debounceMs == 0) {
            dispatchToSubscribers(incoming);
            return;
        }

        // Merge into cross-read accumulator atomically
        accumulator.accumulateAndGet(incoming, (existing, next) ->
                existing == null ? next : existing.merge(next));

        // Schedule one flush per window — compareAndSet ensures only one is scheduled at a time
        if (pendingFlush.compareAndSet(false, true)) {
            flushScheduler.schedule(this::flushAccumulator, debounceMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushAccumulator() {
        // Set false FIRST so listener batches arriving during flush can schedule their own window.
        // If they do, they may also be consumed by this flush's getAndSet — their newly-scheduled
        // flush will then fire as a no-op (null accumulator). This is safe: no data is lost.
        pendingFlush.set(false);
        BatchState snapshot = accumulator.getAndSet(null);
        if (snapshot != null) {
            dispatchToSubscribers(snapshot);
        }
    }

    private void dispatchToSubscribers(BatchState state) {
        for (Subscriber sub : subscribers) {
            boolean wake = sub.eventTypes().isEmpty()                           // subscriber wants all types
                    || state.wildcard()                                         // sender does not know types
                    || !Collections.disjoint(sub.eventTypes(), state.types()); // type intersection
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
