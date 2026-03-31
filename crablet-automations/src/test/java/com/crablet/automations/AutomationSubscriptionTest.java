package com.crablet.automations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AutomationSubscription}.
 */
@DisplayName("AutomationSubscription Tests")
class AutomationSubscriptionTest {

    @Test
    @DisplayName("Given automation name and event types (Set), when creating subscription, then subscription created successfully")
    void givenAutomationNameAndEventTypesAsSet_whenCreatingSubscription_thenSubscriptionCreatedSuccessfully() {
        // Given
        String automationName = "wallet-notification";
        Set<String> eventTypes = Set.of("WalletOpened", "DepositMade");

        // When
        AutomationSubscription subscription = AutomationSubscription.builder(automationName)
            .eventTypes(eventTypes)
            .build();

        // Then
        assertThat(subscription.getAutomationName()).isEqualTo("wallet-notification");
        assertThat(subscription.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
        assertThat(subscription.getRequiredTags()).isEmpty();
        assertThat(subscription.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Given automation name and event types (varargs), when creating subscription, then subscription created successfully")
    void givenAutomationNameAndEventTypesAsVarargs_whenCreatingSubscription_thenSubscriptionCreatedSuccessfully() {
        // When
        AutomationSubscription subscription = AutomationSubscription.builder("wallet-notification")
            .eventTypes("WalletOpened", "DepositMade")
            .build();

        // Then
        assertThat(subscription.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Given automation name with required tags, when creating subscription, then subscription includes required tags")
    void givenAutomationNameWithRequiredTags_whenCreatingSubscription_thenSubscriptionIncludesRequiredTags() {
        // When
        AutomationSubscription subscription = AutomationSubscription.builder("wallet-notification")
            .requiredTags("wallet_id")
            .build();

        // Then
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
        assertThat(subscription.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Given automation name with anyOf tags, when creating subscription, then subscription includes anyOf tags")
    void givenAutomationNameWithAnyOfTags_whenCreatingSubscription_thenSubscriptionIncludesAnyOfTags() {
        // When
        AutomationSubscription subscription = AutomationSubscription.builder("wallet-notification")
            .anyOfTags("wallet_id", "from_wallet_id")
            .build();

        // Then
        assertThat(subscription.getAnyOfTags()).containsExactlyInAnyOrder("wallet_id", "from_wallet_id");
        assertThat(subscription.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("Given automation name with all configuration, when creating subscription, then subscription includes all fields")
    void givenAutomationNameWithAllConfiguration_whenCreatingSubscription_thenSubscriptionIncludesAllFields() {
        // When
        AutomationSubscription subscription = AutomationSubscription.builder("wallet-notification")
            .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade")
            .requiredTags("wallet_id")
            .anyOfTags("from_wallet_id", "to_wallet_id")
            .build();

        // Then
        assertThat(subscription.getAutomationName()).isEqualTo("wallet-notification");
        assertThat(subscription.getEventTypes()).containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade");
        assertThat(subscription.getRequiredTags()).containsExactly("wallet_id");
        assertThat(subscription.getAnyOfTags()).containsExactlyInAnyOrder("from_wallet_id", "to_wallet_id");
    }

    @Test
    @DisplayName("Given null event types, when creating subscription, then event types is empty")
    void givenNullEventTypes_whenCreatingSubscription_thenEventTypesIsEmpty() {
        // When
        AutomationSubscription subscription = new AutomationSubscription("automation", null, null, null);

        // Then
        assertThat(subscription.getEventTypes()).isEmpty();
        assertThat(subscription.getRequiredTags()).isEmpty();
        assertThat(subscription.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("Given subscription, when getting event types, then set is immutable")
    void givenSubscription_whenGettingEventTypes_thenSetIsImmutable() {
        // When
        AutomationSubscription subscription = AutomationSubscription.builder("automation")
            .eventTypes("WalletOpened")
            .build();

        // Then
        assertThat(subscription.getEventTypes()).isUnmodifiable();
        assertThat(subscription.getRequiredTags()).isUnmodifiable();
        assertThat(subscription.getAnyOfTags()).isUnmodifiable();
    }

    @Test
    @DisplayName("Given builder, when chaining methods, then subscription created successfully")
    void givenBuilder_whenChainingMethods_thenSubscriptionCreatedSuccessfully() {
        // When - Verify chaining returns builder
        AutomationSubscription subscription = AutomationSubscription.builder("automation")
            .eventTypes("EventA")
            .requiredTags("tag_a")
            .anyOfTags("tag_b", "tag_c")
            .build();

        // Then
        assertThat(subscription.getAutomationName()).isEqualTo("automation");
        assertThat(subscription.getEventTypes()).containsExactly("EventA");
        assertThat(subscription.getRequiredTags()).containsExactly("tag_a");
        assertThat(subscription.getAnyOfTags()).containsExactlyInAnyOrder("tag_b", "tag_c");
    }

    @Test
    @DisplayName("Given builder with no event types, when building, then event types is empty")
    void givenBuilderWithNoEventTypes_whenBuilding_thenEventTypesIsEmpty() {
        // When
        AutomationSubscription subscription = AutomationSubscription.builder("automation").build();

        // Then
        assertThat(subscription.getEventTypes()).isEmpty();
    }
}
