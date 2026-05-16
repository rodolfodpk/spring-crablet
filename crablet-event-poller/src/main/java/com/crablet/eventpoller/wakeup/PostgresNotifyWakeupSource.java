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
 * <p>Shared across modules: multiple callers register via
 * {@link #start(Set, Set, Set, Set, Runnable)}. The LISTEN connection and listener thread
 * start on the first registration and stop when the last subscriber unregisters.
 * {@link #close()} force-closes regardless of remaining subscribers (context shutdown).
 *
 * <h2>Payload format</h2>
 * <pre>{@code EventType1,EventType2|tagkey1,tagkey2,tagkey3}</pre>
 * Types before {@code |}, tag <em>key names</em> (not values) after. A bare {@code *}
 * or absent segment signals wildcard. Falls back to types-only or {@code *} if encoding
 * exceeds 7 900 bytes. Only tag keys are encoded — high-cardinality values (UUIDs,
 * amounts) are intentionally excluded to keep the payload compact. Exact-tag value
 * matching is left to the SQL filter layer.
 *
 * <h2>Batch coalescing (Phase A)</h2>
 * Multiple notifications in one {@code getNotifications()} call are merged: types unioned,
 * tag keys unioned, any wildcard notification poisons the whole batch.
 *
 * <h2>Cross-read debounce (Phase B)</h2>
 * When {@code debounceMs > 0}, back-to-back reads within the window are further merged.
 *
 * <h2>Full EventSelection filtering (Phase D)</h2>
 * Each subscriber registers its {@link com.crablet.eventpoller.EventSelection} criteria:
 * event types, required tag keys (ALL must be present), anyOf tag keys (at least ONE),
 * and exact tag keys (the key names from exactTags — ALL must be present; exact value
 * matching is conservative at this layer and delegated to SQL).
 *
 * <h2>Reconnect backoff (Phase E)</h2>
 * Transient failures retry with exponential backoff capped at 60 s. Permanent pooler
 * failures exit without retry.
 */
@SuppressWarnings("NullAway") // AtomicReference<BatchState> uses null as "no pending state" sentinel
public final class PostgresNotifyWakeupSource implements ProcessorWakeupSource {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyWakeupSource.class);

    private static final long RECONNECT_BASE_MS   = 1_000L;
    private static final long RECONNECT_MAX_MS    = 60_000L;
    private static final int  RECONNECT_MAX_SHIFT = 6;

    private final String jdbcUrl;
    private final @Nullable String username;
    private final @Nullable String password;
    private final String channel;
    private final long debounceMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    private final AtomicReference<BatchState> accumulator = new AtomicReference<>(null);
    private final AtomicBoolean pendingFlush = new AtomicBoolean(false);
    private final ScheduledExecutorService flushScheduler;

    private @Nullable Thread listenerThread;
    private @Nullable Connection connection;

    // ── Subscriber record ──────────────────────────────────────────────────────

    /**
     * @param eventTypes     event type names; empty = all types
     * @param requiredTagKeys tag keys that ALL must be in the batch (empty = no restriction)
     * @param anyOfTagKeys   tag keys where at least ONE must be in the batch (empty = no restriction)
     * @param exactTagKeys   tag key names from exactTags declarations; ALL must be present
     *                       (value check is conservative — exact values verified by SQL)
     */
    private record Subscriber(
            Set<String> eventTypes,
            Set<String> requiredTagKeys,
            Set<String> anyOfTagKeys,
            Set<String> exactTagKeys,
            Runnable onWakeup) {}

    // ── BatchState record ──────────────────────────────────────────────────────

    /**
     * Immutable accumulated batch state. {@code tagKeys} contains the tag key names
     * (not values) present across all appended events in the batch.
     */
    record BatchState(boolean wildcard, Set<String> types, Set<String> tagKeys) {

        static BatchState ofNotification(boolean wildcard, Set<String> types, Set<String> tagKeys) {
            if (wildcard) return new BatchState(true, Set.of(), Set.of());
            return new BatchState(false, Set.copyOf(types), Set.copyOf(tagKeys));
        }

        BatchState merge(BatchState other) {
            if (this.wildcard || other.wildcard) return new BatchState(true, Set.of(), Set.of());
            Set<String> mt = new HashSet<>(types);   mt.addAll(other.types);
            Set<String> mk = new HashSet<>(tagKeys); mk.addAll(other.tagKeys);
            return new BatchState(false, Collections.unmodifiableSet(mt), Collections.unmodifiableSet(mk));
        }
    }

    // ── Constructors ────────────────────────────────────────────────────────────

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

    // ── ProcessorWakeupSource ───────────────────────────────────────────────────

    @Override
    public synchronized void start(Runnable onWakeup) {
        start(Set.of(), Set.of(), Set.of(), Set.of(), onWakeup);
    }

    @Override
    public synchronized void start(Set<String> eventTypes, Runnable onWakeup) {
        start(eventTypes, Set.of(), Set.of(), Set.of(), onWakeup);
    }

    @Override
    public synchronized void start(Set<String> eventTypes, Set<String> requiredTagKeys,
                                   Set<String> anyOfTagKeys, Set<String> exactTagKeys,
                                   Runnable onWakeup) {
        subscribers.add(new Subscriber(eventTypes, requiredTagKeys, anyOfTagKeys, exactTagKeys, onWakeup));
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

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    private void drainAndStop() {
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

    // ── Reconnect loop ──────────────────────────────────────────────────────────

    private void listenLoop() {
        int attempt = 0;
        while (running.get()) {
            boolean[] wasConnected = {false};
            try {
                runListenSession(wasConnected);
                return;
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

    // ── Batch dispatch ──────────────────────────────────────────────────────────

    void dispatchBatch(PGNotification[] notifications) {
        boolean hasWildcard = false;
        Set<String> batchTypes   = new HashSet<>();
        Set<String> batchTagKeys = new HashSet<>();

        for (PGNotification n : notifications) {
            String payload = n.getParameter();
            if (isWildcard(payload)) { hasWildcard = true; break; }
            batchTypes.addAll(parseTypes(payload));
            batchTagKeys.addAll(parseTagSection(payload));
        }

        BatchState incoming = BatchState.ofNotification(hasWildcard, batchTypes, batchTagKeys);

        if (debounceMs == 0) {
            dispatchToSubscribers(incoming);
            return;
        }

        accumulator.accumulateAndGet(incoming, (existing, next) ->
                existing == null ? next : existing.merge(next));

        if (pendingFlush.compareAndSet(false, true)) {
            flushScheduler.schedule(this::flushAccumulator, debounceMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushAccumulator() {
        pendingFlush.set(false);
        BatchState snapshot = accumulator.getAndSet(null);
        if (snapshot != null) {
            dispatchToSubscribers(snapshot);
        }
    }

    private void dispatchToSubscribers(BatchState state) {
        for (Subscriber sub : subscribers) {
            if (shouldWake(sub, state)) sub.onWakeup().run();
        }
    }

    private static boolean shouldWake(Subscriber sub, BatchState state) {
        if (state.wildcard()) return true;

        // Event-type check
        if (!sub.eventTypes().isEmpty() && Collections.disjoint(sub.eventTypes(), state.types())) {
            return false;
        }

        // Tag checks — skipped when batch carries no tag keys (old-format or no tags on events)
        if (!state.tagKeys().isEmpty()) {
            // requiredTags: ALL declared keys must be present in the batch
            if (!sub.requiredTagKeys().isEmpty() && !state.tagKeys().containsAll(sub.requiredTagKeys())) {
                return false;
            }
            // anyOfTags: at least ONE declared key must be present
            if (!sub.anyOfTagKeys().isEmpty() && Collections.disjoint(sub.anyOfTagKeys(), state.tagKeys())) {
                return false;
            }
            // exactTags (conservative): declared key names must be present; SQL verifies values
            if (!sub.exactTagKeys().isEmpty() && !state.tagKeys().containsAll(sub.exactTagKeys())) {
                return false;
            }
        }

        return true;
    }

    // ── Payload parsing ─────────────────────────────────────────────────────────

    static boolean isWildcard(@Nullable String payload) {
        return payload == null || payload.isBlank() || "*".equals(payload.trim());
    }

    /** Parse the types section (before {@code |}) of a notification payload. */
    static Set<String> parseTypes(@Nullable String payload) {
        if (isWildcard(payload)) return Set.of();
        String part = payload.contains("|") ? payload.substring(0, payload.indexOf('|')) : payload;
        return parseCommaSeparated(part);
    }

    /** Parse the tag-keys section (after {@code |}) of a notification payload. */
    static Set<String> parseTagSection(@Nullable String payload) {
        if (isWildcard(payload)) return Set.of();
        int pipe = payload == null ? -1 : payload.indexOf('|');
        if (pipe < 0 || pipe == payload.length() - 1) return Set.of();
        return parseCommaSeparated(payload.substring(pipe + 1));
    }

    /** Kept for backward-compatibility with existing tests (parses types only). */
    static Set<String> parsePayload(@Nullable String payload) {
        return parseTypes(payload);
    }

    private static Set<String> parseCommaSeparated(@Nullable String s) {
        if (s == null || s.isBlank()) return Set.of();
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    // ── Lifecycle helpers ────────────────────────────────────────────────────────

    private void closeConnectionQuietly() {
        if (connection == null) return;
        try {
            if (!connection.isClosed()) connection.close();
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
