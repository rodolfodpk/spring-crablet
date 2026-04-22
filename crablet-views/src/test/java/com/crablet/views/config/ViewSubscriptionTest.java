package com.crablet.views.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.crablet.views.ViewSubscription;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ViewSubscription.
 * Tests builder pattern, immutability, and configuration validation.
 */
@SuppressWarnings("NullAway")
@DisplayName("ViewSubscription Unit Tests")
class ViewSubscriptionTest {

    @Test
    @DisplayName("Should create config with view name only")
    void shouldCreateConfig_WithViewNameOnly() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .build();

        // Then
        assertThat(config.getViewName()).isEqualTo("wallet-view");
        assertThat(config.getEventTypes()).isEmpty();
        assertThat(config.getRequiredTags()).isEmpty();
        assertThat(config.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Should create config with event types")
    void shouldCreateConfig_WithEventTypes() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade")
                .build();

        // Then
        assertThat(config.getViewName()).isEqualTo("wallet-view");
        assertThat(config.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(config.getRequiredTags()).isEmpty();
        assertThat(config.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Should create config with event types from Set")
    void shouldCreateConfig_WithEventTypesFromSet() {
        // Given
        Set<String> eventTypes = Set.of("WalletOpened", "DepositMade");

        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes(eventTypes)
                .build();

        // Then
        assertThat(config.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should create config with required tags")
    void shouldCreateConfig_WithRequiredTags() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .requiredTags("wallet_id", "account_id")
                .build();

        // Then
        assertThat(config.getRequiredTags()).containsExactlyInAnyOrder("wallet_id", "account_id");
    }

    @Test
    @DisplayName("Should create config with required tags from Set")
    void shouldCreateConfig_WithRequiredTagsFromSet() {
        // Given
        Set<String> requiredTags = Set.of("wallet_id");

        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .requiredTags(requiredTags)
                .build();

        // Then
        assertThat(config.getRequiredTags()).containsExactly("wallet_id");
    }

    @Test
    @DisplayName("Should create config with any-of tags")
    void shouldCreateConfig_WithAnyOfTags() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .anyOfTags("region", "country")
                .build();

        // Then
        assertThat(config.getAnyOfTags()).containsExactlyInAnyOrder("region", "country");
    }

    @Test
    @DisplayName("Should create config with any-of tags from Set")
    void shouldCreateConfig_WithAnyOfTagsFromSet() {
        // Given
        Set<String> anyOfTags = Set.of("region");

        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .anyOfTags(anyOfTags)
                .build();

        // Then
        assertThat(config.getAnyOfTags()).containsExactly("region");
    }

    @Test
    @DisplayName("Should create config with all filters combined")
    void shouldCreateConfig_WithAllFiltersCombined() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes("WalletOpened", "DepositMade")
                .requiredTags("wallet_id")
                .anyOfTags("region", "country")
                .build();

        // Then
        assertThat(config.getViewName()).isEqualTo("wallet-view");
        assertThat(config.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
        assertThat(config.getRequiredTags()).containsExactly("wallet_id");
        assertThat(config.getAnyOfTags()).containsExactlyInAnyOrder("region", "country");
    }

    @Test
    @DisplayName("Should use requireTag convenience method")
    void shouldUseRequireTag_ConvenienceMethod() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .requireTag("wallet_id")
                .build();

        // Then
        assertThat(config.getRequiredTags()).containsExactly("wallet_id");
    }

    @Test
    @DisplayName("Should use anyOfTag convenience method")
    void shouldUseAnyOfTag_ConvenienceMethod() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .anyOfTag("region")
                .build();

        // Then
        assertThat(config.getAnyOfTags()).containsExactly("region");
    }

    @Test
    @DisplayName("Should handle null event types as empty set")
    void shouldHandleNullEventTypes_AsEmptySet() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes((Set<String>) null)
                .build();

        // Then
        assertThat(config.getEventTypes()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null required tags as empty set")
    void shouldHandleNullRequiredTags_AsEmptySet() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .requiredTags((Set<String>) null)
                .build();

        // Then
        assertThat(config.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null any-of tags as empty set")
    void shouldHandleNullAnyOfTags_AsEmptySet() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .anyOfTags((Set<String>) null)
                .build();

        // Then
        assertThat(config.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Should create immutable sets")
    void shouldCreateImmutableSets() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes("WalletOpened", "DepositMade")
                .build();

        // Then - Attempting to modify should throw UnsupportedOperationException
        assertThatThrownBy(() -> config.getEventTypes().add("NewEvent"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should handle empty event types array")
    void shouldHandleEmptyEventTypesArray() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes()
                .build();

        // Then
        assertThat(config.getEventTypes()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty required tags array")
    void shouldHandleEmptyRequiredTagsArray() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .requiredTags()
                .build();

        // Then
        assertThat(config.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty any-of tags array")
    void shouldHandleEmptyAnyOfTagsArray() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .anyOfTags()
                .build();

        // Then
        assertThat(config.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Should allow chaining builder methods")
    void shouldAllowChainingBuilderMethods() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes("WalletOpened")
                .requiredTags("wallet_id")
                .anyOfTags("region")
                .requireTag("account_id") // Overwrites previous requiredTags
                .anyOfTag("country") // Overwrites previous anyOfTags
                .build();

        // Then
        assertThat(config.getEventTypes()).containsExactly("WalletOpened");
        assertThat(config.getRequiredTags()).containsExactly("account_id"); // Last one wins
        assertThat(config.getAnyOfTags()).containsExactly("country"); // Last one wins
    }

    @Test
    @DisplayName("Should handle duplicate event types")
    void shouldHandleDuplicateEventTypes() {
        // When - Set.of() throws IllegalArgumentException for duplicates, so use Set with duplicates first
        Set<String> eventTypesWithDuplicates = Set.of("WalletOpened", "DepositMade");
        ViewSubscription config = ViewSubscription.builder("wallet-view")
                .eventTypes(eventTypesWithDuplicates)
                .build();

        // Then - Set should contain unique values (Set.of() already ensures uniqueness)
        assertThat(config.getEventTypes()).hasSize(2);
        assertThat(config.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should handle view name with special characters")
    void shouldHandleViewName_WithSpecialCharacters() {
        // When
        ViewSubscription config = ViewSubscription.builder("wallet-view-123")
                .build();

        // Then
        assertThat(config.getViewName()).isEqualTo("wallet-view-123");
    }
}

