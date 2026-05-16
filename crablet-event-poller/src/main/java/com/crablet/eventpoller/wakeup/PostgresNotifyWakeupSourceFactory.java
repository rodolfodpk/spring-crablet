package com.crablet.eventpoller.wakeup;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;

/**
 * Factory for the shared PostgreSQL LISTEN/NOTIFY wakeup source.
 *
 * <p>{@link #create()} always returns the same {@link PostgresNotifyWakeupSource} instance.
 * Multiple modules (views, automations, outbox) each register their own subscriber via
 * {@link ProcessorWakeupSource#start(Runnable)}, sharing one LISTEN connection.
 *
 * <p>{@link #close()} is called on Spring context shutdown via {@link PreDestroy} and
 * force-closes the shared connection regardless of remaining subscribers.
 */
public final class PostgresNotifyWakeupSourceFactory implements ProcessorWakeupSourceFactory {

    private final PostgresNotifyWakeupSource shared;

    public PostgresNotifyWakeupSourceFactory(
            String jdbcUrl, @Nullable String username, @Nullable String password, String channel) {
        this(jdbcUrl, username, password, channel, 20L);
    }

    public PostgresNotifyWakeupSourceFactory(
            String jdbcUrl, @Nullable String username, @Nullable String password,
            String channel, long debounceMs) {
        this.shared = new PostgresNotifyWakeupSource(jdbcUrl, username, password, channel, debounceMs);
    }

    @Override
    public ProcessorWakeupSource create() {
        return shared;
    }

    @PreDestroy
    public void close() {
        shared.close();
    }
}
