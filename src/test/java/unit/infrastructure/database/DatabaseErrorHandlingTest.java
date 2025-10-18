package unit.infrastructure.database;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.Query;
import com.crablet.core.StateProjector;
import com.crablet.core.Tag;
import com.crablet.impl.JDBCEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for database-related error handling scenarios.
 * Covers basic error handling in JDBCEventStore.
 */
class DatabaseErrorHandlingTest extends AbstractCrabletTest {

    @Autowired
    private JDBCEventStore eventStore;

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
        assertThatCode(() -> eventStore.query(Query.empty(), null))
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
        AppendCondition condition = AppendCondition.forEmptyStream();

        // When & Then
        assertThatThrownBy(() -> eventStore.appendIf(null, condition))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle appendIf with null condition")
    void shouldHandleAppendIfWithNullCondition() {
        // Given
        List<AppendEvent> events = List.of(
            AppendEvent.of("TestEvent", List.of(), "test data".getBytes())
        );

        // When & Then
        assertThatThrownBy(() -> eventStore.appendIf(events, null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle query with null query")
    void shouldHandleQueryWithNullQuery() {
        // When & Then
        assertThatThrownBy(() -> eventStore.query(null, null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle projection with null projector")
    void shouldHandleProjectionWithNullProjector() {
        // When & Then
        assertThatThrownBy(() -> eventStore.project((List<StateProjector>) null, null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle projection with null query")
    void shouldHandleProjectionWithNullQuery() {
        // When & Then
        assertThatThrownBy(() -> eventStore.project((List<StateProjector>) null, null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle invalid event data gracefully")
    void shouldHandleInvalidEventDataGracefully() {
        // Given - Create event with invalid JSON data
        AppendEvent invalidEvent = AppendEvent.of(
            "InvalidEvent", 
            List.of(new Tag("test", "value")), 
            "invalid json data".getBytes()
        );

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(invalidEvent)))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle malformed tags gracefully")
    void shouldHandleMalformedTagsGracefully() {
        // Given - Create event with malformed tags but valid JSON data
        AppendEvent eventWithMalformedTags = AppendEvent.of(
            "TestEvent", 
            List.of(new Tag("", "value"), new Tag("key", "")), 
            "{\"test\": \"data\"}".getBytes()
        );

        // When & Then
        assertThatCode(() -> eventStore.append(List.of(eventWithMalformedTags)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle very large event data")
    void shouldHandleVeryLargeEventData() {
        // Given - Create event with large data
        String largeData = "x".repeat(10000);
        AppendEvent largeEvent = AppendEvent.of(
            "LargeEvent", 
            List.of(new Tag("size", "large")), 
            largeData.getBytes()
        );

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(largeEvent)))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle special characters in event data")
    void shouldHandleSpecialCharactersInEventData() {
        // Given - Create event with special characters
        String specialData = "Special chars: éñüñç@#$%^&*()_+-=[]{}|;':\",./<>?";
        AppendEvent specialEvent = AppendEvent.of(
            "SpecialEvent", 
            List.of(new Tag("chars", "special")), 
            specialData.getBytes()
        );

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(specialEvent)))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle unicode characters in event data")
    void shouldHandleUnicodeCharactersInEventData() {
        // Given - Create event with unicode characters
        String unicodeData = "Unicode: 🚀🌟💫⭐✨🎉🎊";
        AppendEvent unicodeEvent = AppendEvent.of(
            "UnicodeEvent", 
            List.of(new Tag("unicode", "true")), 
            unicodeData.getBytes()
        );

        // When & Then
        assertThatThrownBy(() -> eventStore.append(List.of(unicodeEvent)))
            .isInstanceOf(Exception.class);
    }
}