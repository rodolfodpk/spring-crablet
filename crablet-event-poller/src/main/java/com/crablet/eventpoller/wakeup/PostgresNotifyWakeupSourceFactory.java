package com.crablet.eventpoller.wakeup;

/**
 * Factory for dedicated PostgreSQL LISTEN/NOTIFY wakeup listeners.
 */
public final class PostgresNotifyWakeupSourceFactory implements ProcessorWakeupSourceFactory {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String channel;

    public PostgresNotifyWakeupSourceFactory(String jdbcUrl, String username, String password, String channel) {
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
