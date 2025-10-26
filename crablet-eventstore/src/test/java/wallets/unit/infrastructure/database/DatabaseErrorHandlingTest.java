package wallets.unit.infrastructure.database;
import wallets.integration.AbstractWalletIntegrationTest;

import com.crablet.eventstore.AppendCondition;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.Cursor;
import com.crablet.eventstore.Query;
import com.crablet.eventstore.StateProjector;
import com.crablet.eventstore.Tag;
import com.crablet.eventstore.impl.EventStoreImpl;
import com.crablet.eventstore.EventTestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import wallets.integration.AbstractWalletIntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for database-related error handling scenarios.
 * Covers basic error handling in EventStoreImpl.
 */
class DatabaseErrorHandlingTest extends AbstractWalletIntegrationTest {

    @Autowired
    private EventStoreImpl eventStore;

    @Autowired
    private EventTestHelper testHelper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInputGracefully() {
        // When & Then
        assertThatThrownBy(() -> eventStore.append(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle empty query gracefully")
    void shouldHandleEmptyQueryGracefully() {
        // When & Then
        assertThatCode(() -> testHelper.query(Query.empty(), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle empty append gracefully")
    void shouldHandleEmptyAppendGracefully() {
        // When & Then
        assertThatCode(() -> eventStore.append(List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle appendIf with null events")
    void shouldHandleAppendIfWithNullEvents() {
        // Given
        AppendCondition condition = AppendCondition.expectEmptyStream();

        // When & Then
        assertThatThrownBy(() -> eventStore.appendIf(null, condition))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle appendIf with null condition")
    void shouldHandleAppendIfWithNullCondition() {
        // Given
        List<AppendEvent> events = List.of(
                AppendEvent.builder("TestEvent").data("test data").build()
        );

        // When & Then
        assertThatThrownBy(() -> eventStore.appendIf(events, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle query with null query")
    void shouldHandleQueryWithNullQuery() {
        // When & Then
        assertThatThrownBy(() -> testHelper.query(null, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle projection with null query")
    void shouldHandleProjectionWithNullQuery() {
        // When & Then
        assertThatThrownBy(() -> eventStore.project(null, Cursor.zero(), Object.class, List.of()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle invalid event data gracefully")
    void shouldHandleInvalidEventDataGracefully() {
        // Given - Create event with invalid JSON data
        AppendEvent invalidEvent = AppendEvent.builder("InvalidEvent")
                .tag("test", "value")
                .data("invalid json data")
                .build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(invalidEvent)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle malformed tags gracefully")
    void shouldHandleMalformedTagsGracefully() {
        // Given - Create event with malformed tags but valid JSON data
        AppendEvent eventWithMalformedTags = AppendEvent.builder("TestEvent")
                .tag("", "value")
                .tag("key", "")
                .data("{\"test\": \"data\"}")
                .build();

        // When & Then
        assertThatCode(() -> eventStore.append(List.of(eventWithMalformedTags)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle very large event data")
    void shouldHandleVeryLargeEventData() {
        // Given - Create event with large data
        String largeData = "x".repeat(10000);
        AppendEvent largeEvent = AppendEvent.builder("LargeEvent")
                .tag("size", "large")
                .data(largeData)
                .build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(largeEvent)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle special characters in event data")
    void shouldHandleSpecialCharactersInEventData() {
        // Given - Create event with special characters
        String specialData = "Special chars: éñüñç@#$%^&*()_+-=[]{}|;':\",./<>?";
        AppendEvent specialEvent = AppendEvent.builder("SpecialEvent")
                .tag("chars", "special")
                .data(specialData)
                .build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(specialEvent)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle unicode characters in event data")
    void shouldHandleUnicodeCharactersInEventData() {
        // Given - Create event with unicode characters
        String unicodeData = "Unicode: 🚀🌟💫⭐✨🎉🎊";
        AppendEvent unicodeEvent = AppendEvent.builder("UnicodeEvent")
                .tag("unicode", "true")
                .data(unicodeData)
                .build();

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(unicodeEvent)))
                .isInstanceOf(Exception.class);
    }
}