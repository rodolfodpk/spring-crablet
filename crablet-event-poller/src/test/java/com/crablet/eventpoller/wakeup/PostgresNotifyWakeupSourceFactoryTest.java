package com.crablet.eventpoller.wakeup;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.crablet.eventpoller.wakeup.PostgresNotifyWakeupTestProperties.channel;
import static com.crablet.eventpoller.wakeup.PostgresNotifyWakeupTestProperties.jdbcUrl;
import static com.crablet.eventpoller.wakeup.PostgresNotifyWakeupTestProperties.password;
import static com.crablet.eventpoller.wakeup.PostgresNotifyWakeupTestProperties.username;

import static org.assertj.core.api.Assertions.assertThatCode;

class PostgresNotifyWakeupSourceFactoryTest {

    @Test
    void preDestroyCloseStopsSharedSource() {
        PostgresNotifyWakeupSourceFactory factory = new PostgresNotifyWakeupSourceFactory(
                jdbcUrl(), username(), password(), channel(), 0L);

        ProcessorWakeupSource shared = factory.create();
        AtomicInteger woke = new AtomicInteger();
        shared.start(woke::incrementAndGet);

        assertThatCode(factory::close).doesNotThrowAnyException();
    }
}
