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
 *
 * <h2>Reconnect backoff (Phase E)</h2>
 * <p>Transient LISTEN failures (Postgres restart, network blip) are retried with
 * exponential backoff capped at 60 s. A connection that was established and then dropped
 * resets the backoff to 1 s because the server was reachable. Permanent failures (pooler
 * detected via {@code unwrap} failure) exit without retry; the system falls back to
 * scheduled polling.
 */
@SuppressWarnings("NullAway") // AtomicReference<BatchState> uses null as "no pending state" sentinel
public final class PostgresNotifyWakeupSource implements ProcessorWakeupSource {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyWakeupSource.class);

    // Reconnect backoff: 1 s → 2 s → 4 s → … capped at 60 s
    private static final long RECONNECT_BASE_MS    = 1_000L;
    private static final long RECONNECT_MAX_MS     = 60_000L;
    private static final int  RECONNECT_MAX_SHIFT  = 6; // 2^6 × 1 s = 64 s > cap

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

    // ── Reconnect loop ────────────────────────────────────────────────────────

    private void listenLoop() {
        int attempt = 0;
        while (running.get()) {
            boolean[] wasConnected = {false};
            try {
                runListenSession(wasConnected);
                return; // clean shutdown (running became false)
            } catch (SQLException e) {
                if (!running.get()) return;

                if (isPermanentFailure(e)) {
                    log.warn("LISTEN wakeup permanently disabled for channel '{}': "
                            + "could not obtain a direct PostgreSQL connection. "
                            + "Check that notifications.jdbc-url points directly at PostgreSQL "
                            + "and not through a pooler (PgBouncer transaction mode, PgCat, "
                            + "RDS Proxy). Falling back to scheduled polling. Cause: {}",
                            channel, e.getMessage());
                    return;
                }

                long delayMs;
                if (wasConnected[0]) {
                    // Server was reachable — transient drop (restart, network blip); reset backoff
                    attempt = 0;
                    delayMs = RECONNECT_BASE_MS;
                    log.warn("LISTEN connection dropped for channel '{}', reconnecting in {}ms: {}",
                            channel, delayMs, e.getMessage());
                } else {
                    delayMs = Math.min(RECONNECT_BASE_MS << attempt, RECONNECT_MAX_MS);
                    log.warn("LISTEN could not connect for channel '{}' (attempt {}), "
                            + "retrying in {}ms: {}",
                            channel, attempt + 1, delayMs, e.getMessage());
                    attempt = Math.min(attempt + 1, RECONNECT_MAX_SHIFT);
                }

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Open one LISTEN session and poll until {@code running} is false or an error occurs.
     * Sets {@code wasConnected[0] = true} once the LISTEN statement succeeds, so the
     * caller can distinguish "never connected" from "connected then dropped."
     */
    private void runListenSession(boolean[] wasConnected) throws SQLException {
        Connection listenConnection = username == null && password == null
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, username, password);
        connection = listenConnection;
        try {
            try (Statement statement = listenConnection.createStatement()) {
                statement.execute("LISTEN " + channel);
            }
            PGConnection pgConnection = listenConnection.unwrap(PGConnection.class);
            wasConnected[0] = true;
            log.debug("LISTEN active on channel '{}'", channel);

            while (running.get()) {
                PGNotification[] notifications = pgConnection.getNotifications(1000);
                if (notifications == null || notifications.length == 0) continue;
                if (!running.get()) break;
                dispatchBatch(notifications);
            }
        } finally {
            closeConnectionQuietly();
        }
    }

    private static boolean isPermanentFailure(SQLException e) {
        return e.getMessage() != null && e.getMessage().toLowerCase().contains("unwrap");
    }

    // ── Batch dispatch ─────────────────────────────────────────────────────────

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

    // ── Payload parsing ────────────────────────────────────────────────────────

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

    // ── Lifecycle helpers ──────────────────────────────────────────────────────

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
