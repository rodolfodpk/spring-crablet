package com.crablet.views;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ViewSubscription}.
 */
@DisplayName("ViewSubscription Tests")
class ViewSubscriptionTest {

    @Test
    @DisplayName("Given view name and event types, when creating subscription, then subscription created successfully")
    void givenViewNameAndEventTypes_whenCreatingSubscription_thenSubscriptionCreatedSuccessfully() {
        // Given
        String viewName = "wallet-view";
        Set<String> eventTypes = Set.of("WalletOpened", "DepositMade");

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .eventTypes(eventTypes)
            .build();

        // Then
        assertThat(subscription.getViewName()).isEqualTo("wallet-view");
        assertThat(subscription.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
        assertThat(subscription.getRequiredTags()).isEmpty();
        assertThat(subscription.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Given view name with required tags, when creating subscription, then subscription includes required tags")
    void givenViewNameWithRequiredTags_whenCreatingSubscription_thenSubscriptionIncludesRequiredTags() {
        // Given
        String viewName = "wallet-view";
        Set<String> requiredTags = Set.of("wallet_id");

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .requiredTags(requiredTags)
            .build();

        // Then
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
    }

    @Test
    @DisplayName("Given view name with anyOf tags, when creating subscription, then subscription includes anyOf tags")
    void givenViewNameWithAnyOfTags_whenCreatingSubscription_thenSubscriptionIncludesAnyOfTags() {
        // Given
        String viewName = "wallet-view";
        Set<String> anyOfTags = Set.of("wallet_id", "from_wallet_id");

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .anyOfTags(anyOfTags)
            .build();

        // Then
        assertThat(subscription.getAnyOfTags()).containsExactlyInAnyOrder("wallet_id", "from_wallet_id");
    }

    @Test
    @DisplayName("Given view name with all configuration, when creating subscription, then subscription includes all fields")
    void givenViewNameWithAllConfiguration_whenCreatingSubscription_thenSubscriptionIncludesAllFields() {
        // Given
        String viewName = "wallet-view";
        Set<String> eventTypes = Set.of("WalletOpened", "DepositMade", "WithdrawalMade");
        Set<String> requiredTags = Set.of("wallet_id");
        Set<String> anyOfTags = Set.of("from_wallet_id", "to_wallet_id");

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .eventTypes(eventTypes)
            .requiredTags(requiredTags)
            .anyOfTags(anyOfTags)
            .build();

        // Then
        assertThat(subscription.getViewName()).isEqualTo("wallet-view");
        assertThat(subscription.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
        assertThat(subscription.getAnyOfTags()).containsExactlyInAnyOrder("from_wallet_id", "to_wallet_id");
    }

    @Test
    @DisplayName("Given view name with convenience methods, when creating subscription, then subscription created successfully")
    void givenViewNameWithConvenienceMethods_whenCreatingSubscription_thenSubscriptionCreatedSuccessfully() {
        // Given
        String viewName = "wallet-view";

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .eventTypes("WalletOpened", "DepositMade")
            .requiredTags("wallet_id")
            .anyOfTags("from_wallet_id", "to_wallet_id")
            .build();

        // Then
        assertThat(subscription.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
        assertThat(subscription.getAnyOfTags()).containsExactlyInAnyOrder("from_wallet_id", "to_wallet_id");
    }

    @Test
    @DisplayName("Given view name with requireTag convenience method, when creating subscription, then subscription includes required tag")
    void givenViewNameWithRequireTagConvenienceMethod_whenCreatingSubscription_thenSubscriptionIncludesRequiredTag() {
        // Given
        String viewName = "wallet-view";

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .requireTag("wallet_id")
            .build();

        // Then
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
    }

    @Test
    @DisplayName("Given view name with anyOfTag convenience method, when creating subscription, then subscription includes anyOf tag")
    void givenViewNameWithAnyOfTagConvenienceMethod_whenCreatingSubscription_thenSubscriptionIncludesAnyOfTag() {
        // Given
        String viewName = "wallet-view";

        // When
        ViewSubscription subscription = ViewSubscription.builder(viewName)
            .anyOfTag("wallet_id")
            .build();

        // Then
        assertThat(subscription.getAnyOfTags()).containsExactly("wallet_id");
    }

    @Test
    @DisplayName("Given view subscription, when using constructor directly, then subscription created successfully")
    void givenViewSubscription_whenUsingConstructorDirectly_thenSubscriptionCreatedSuccessfully() {
        // Given
        String viewName = "wallet-view";
        Set<String> eventTypes = Set.of("WalletOpened");
        Set<String> requiredTags = Set.of("wallet_id");
        Set<String> anyOfTags = Set.of();

        // When
        ViewSubscription subscription = new ViewSubscription(viewName, eventTypes, requiredTags, anyOfTags);

        // Then
        assertThat(subscription.getViewName()).isEqualTo("wallet-view");
        assertThat(subscription.getEventTypes()).containsExactly("WalletOpened");
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
    }
}
