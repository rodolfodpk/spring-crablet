package com.crablet.eventpoller.wakeup;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Loads {@code poller-notify-wakeup-test.properties} using the same keys as
 * {@link com.crablet.eventpoller.config.EventPollerNotificationProperties}.
 */
final class PostgresNotifyWakeupTestProperties {

    private static final Properties PROPS = load();

    private PostgresNotifyWakeupTestProperties() {}

    private static Properties load() {
        try (InputStream in = PostgresNotifyWakeupTestProperties.class.getResourceAsStream(
                "/poller-notify-wakeup-test.properties")) {
            if (in == null) {
                throw new IllegalStateException("classpath:/poller-notify-wakeup-test.properties not found");
            }
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String jdbcUrl() {
        return PROPS.getProperty("crablet.event-poller.notifications.jdbc-url");
    }

    static String username() {
        return PROPS.getProperty("crablet.event-poller.notifications.username");
    }

    static String password() {
        return PROPS.getProperty("crablet.event-poller.notifications.password");
    }

    static String channel() {
        return PROPS.getProperty("crablet.event-poller.notifications.channel");
    }

    static PostgresNotifyWakeupSource newSource(long debounceMs) {
        return new PostgresNotifyWakeupSource(jdbcUrl(), username(), password(), channel(), debounceMs);
    }

    static PostgresNotifyWakeupSource newSourceDefaultDebounce() {
        return new PostgresNotifyWakeupSource(jdbcUrl(), username(), password(), channel());
    }
}
