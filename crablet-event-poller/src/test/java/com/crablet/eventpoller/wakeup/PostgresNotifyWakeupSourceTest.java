package com.crablet.eventpoller.wakeup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostgresNotifyWakeupSource Unit Tests")
class PostgresNotifyWakeupSourceTest {

    @Test
    @DisplayName("Should accept valid PostgreSQL notification channel names")
    void shouldAcceptValidChannelNames() {
        assertThatCode(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "_events_123"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject invalid PostgreSQL notification channel names")
    @SuppressWarnings("NullAway")
    void shouldRejectInvalidChannelNames() {
        assertThatThrownBy(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PostgreSQL notification channel: null");
        assertThatThrownBy(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "123_events"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PostgreSQL notification channel: 123_events");
        assertThatThrownBy(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "events; DROP TABLE events"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PostgreSQL notification channel: events; DROP TABLE events");
    }

    @Test
    @DisplayName("Should close safely before listener starts")
    void shouldCloseSafelyBeforeListenerStarts() {
        // Given
        PostgresNotifyWakeupSource source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events");

        // Then
        assertThatCode(source::close).doesNotThrowAnyException();
    }
}
