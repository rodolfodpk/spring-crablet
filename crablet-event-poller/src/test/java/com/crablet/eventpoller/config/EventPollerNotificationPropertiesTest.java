package com.crablet.eventpoller.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EventPollerNotificationPropertiesTest {

    @Test
    void debounceAndJdbcGettersRoundTrip() {
        EventPollerNotificationProperties props = new EventPollerNotificationProperties();
        props.setChannel("my_channel");
        props.setJdbcUrl("jdbc:postgresql://localhost/db");
        props.setUsername("u");
        props.setPassword("p");
        props.setDebounce(Duration.ofMillis(35));

        assertThat(props.getChannel()).isEqualTo("my_channel");
        assertThat(props.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost/db");
        assertThat(props.getUsername()).isEqualTo("u");
        assertThat(props.getPassword()).isEqualTo("p");
        assertThat(props.getDebounce()).isEqualTo(Duration.ofMillis(35));
    }
}
