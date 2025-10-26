package com.crablet.outbox;

import com.crablet.outbox.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TopicConfig Unit Tests")
class TopicConfigTest {

    @Test
    @DisplayName("Should match when all required tags are present")
    void shouldMatchWhenAllRequiredTagsPresent() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet", "user")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123",
                "user", "user-456",
                "extra", "extra-value"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should not match when required tag is missing")
    void shouldNotMatchWhenRequiredTagMissing() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet", "user")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123"
                // Missing "user" tag
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should match when anyOf tag is present")
    void shouldMatchWhenAnyOfTagPresent() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .anyOfTags("priority", "urgent", "critical")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "priority", "high"
                // Has one of the anyOf tags
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should not match when no anyOf tag is present")
    void shouldNotMatchWhenNoAnyOfTagPresent() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .anyOfTags("priority", "urgent", "critical")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123"
                // No anyOf tags present
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should match without anyOf tags configured")
    void shouldMatchWithoutAnyOfTags() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should match exact tag value")
    void shouldMatchExactTagValue() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .exactTag("type", "transfer")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "type", "transfer",
                "wallet", "wallet-123"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should not match wrong exact tag value")
    void shouldNotMatchWrongExactTagValue() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .exactTag("type", "transfer")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "type", "deposit", // Wrong value
                "wallet", "wallet-123"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should match complex combination of all conditions")
    void shouldMatchComplexCombination() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet", "user")
                .anyOfTags("priority", "urgent")
                .exactTag("type", "transfer")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123",     // Required
                "user", "user-456",         // Required
                "priority", "high",         // AnyOf
                "type", "transfer",         // Exact
                "extra", "extra-value"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should not match when any condition fails")
    void shouldNotMatchWhenAnyConditionFails() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet", "user")
                .anyOfTags("priority", "urgent")
                .exactTag("type", "transfer")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123",     // Required ✓
                "user", "user-456",         // Required ✓
                "type", "transfer",         // Exact ✓
                // Missing anyOf tags ✗
                "extra", "extra-value"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should match empty configuration")
    void shouldMatchEmptyConfiguration() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123",
                "user", "user-456"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should handle null event tags")
    void shouldHandleNullEventTags() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet")
                .build();

        // When
        boolean matches = config.matches(null);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should handle empty event tags")
    void shouldHandleEmptyEventTags() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .requireTags("wallet")
                .build();
        
        Map<String, String> eventTags = Map.of();

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should match when exact tag is missing but not required")
    void shouldMatchWhenExactTagMissingButNotRequired() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .exactTag("optional", "value")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "wallet", "wallet-123"
                // Missing "optional" tag
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should not match when exact tag value is null")
    void shouldNotMatchWhenExactTagValueIsNull() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
                .exactTag("type", "transfer")
                .build();
        
        Map<String, String> eventTags = Map.of(
                "type", "transfer",
                "wallet", "wallet-123"
        );
        
        // Create a map with null value using HashMap
        Map<String, String> eventTagsWithNull = new HashMap<>(eventTags);
        eventTagsWithNull.put("type", null);

        // When
        boolean matches = config.matches(eventTagsWithNull);

        // Then
        assertThat(matches).isFalse();
    }
}
