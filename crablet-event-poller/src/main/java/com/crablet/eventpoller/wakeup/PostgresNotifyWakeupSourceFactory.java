package com.crablet.eventpoller.wakeup;

import org.jspecify.annotations.Nullable;

/**
 * Factory for dedicated PostgreSQL LISTEN/NOTIFY wakeup listeners.
 */
public final class PostgresNotifyWakeupSourceFactory implements ProcessorWakeupSourceFactory {

    private final String jdbcUrl;
    private final @Nullable String username;
    private final @Nullable String password;
    private final String channel;

    public PostgresNotifyWakeupSourceFactory(
            String jdbcUrl, @Nullable String username, @Nullable String password, String channel) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.channel = channel;
    }

    @Override
    public ProcessorWakeupSource create() {
        return new PostgresNotifyWakeupSource(jdbcUrl, username, password, channel);
    }
}
