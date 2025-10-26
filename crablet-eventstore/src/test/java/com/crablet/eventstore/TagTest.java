package com.crablet.eventstore;

import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Tag helper methods.
 * Tests DCB-compliant tag creation patterns.
 */
class TagTest {

    @Test
    void shouldCreateSingleTagAsList() {
        // Given
        String key = "wallet_id";
        String value = "wallet-123";

        // When
        List<Tag> tags = Tag.single(key, value);

        // Then
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0)).isEqualTo(new Tag(key, value));
        assertThat(tags.get(0).key()).isEqualTo(key);
        assertThat(tags.get(0).value()).isEqualTo(value);
    }

    @Test
    void shouldCreateMultipleTagsWithVarargs() {
        // Given
        String key1 = "wallet_id";
        String value1 = "w1";
        String key2 = "deposit_id";
        String value2 = "d1";

        // When
        List<Tag> tags = Tag.of(key1, value1, key2, value2);

        // Then
        assertThat(tags).hasSize(2);
        assertThat(tags.get(0)).isEqualTo(new Tag(key1, value1));
        assertThat(tags.get(1)).isEqualTo(new Tag(key2, value2));
    }

    @Test
    void shouldCreateStateIdentifierTag() {
        // Given
        String stateName = "Wallet";
        String stateId = "wallet-123";

        // When
        Tag tag = Tag.stateIdentifier(stateName, stateId);

        // Then
        assertThat(tag.key()).isEqualTo("state_identifier");
        assertThat(tag.value()).isEqualTo("wallet-123@Wallet");
    }

    @Test
    void shouldCreateEventTypeTag() {
        // Given
        String eventName = "WalletOpened";

        // When
        Tag tag = Tag.eventType(eventName);

        // Then
        assertThat(tag.key()).isEqualTo("event_type");
        assertThat(tag.value()).isEqualTo(eventName);
    }

    @Test
    void shouldCreateStateIdentifiersForMultipleStates() {
        // Given
        String stateName1 = "Wallet";
        String stateId1 = "w1";
        String stateName2 = "Deposit";
        String stateId2 = "d1";

        // When
        List<Tag> tags = Tag.stateIdentifiers(stateName1, stateId1, stateName2, stateId2);

        // Then
        assertThat(tags).hasSize(2);
        assertThat(tags.get(0)).isEqualTo(Tag.stateIdentifier(stateName1, stateId1));
        assertThat(tags.get(1)).isEqualTo(Tag.stateIdentifier(stateName2, stateId2));
    }

    @Test
    void shouldSupportDCBQueryPatterns() {
        // Test that Tag.single() works well with DCB query patterns
        // This is the most common pattern: single tag for entity identification

        // Given - wallet identification tag
        List<Tag> walletTag = Tag.single("wallet_id", "wallet-123");

        // When - used in DCB query context
        // This would typically be used with Query.forEventAndTags()

        // Then - should be a single-element list
        assertThat(walletTag).hasSize(1);
        assertThat(walletTag.get(0).key()).isEqualTo("wallet_id");
        assertThat(walletTag.get(0).value()).isEqualTo("wallet-123");
    }
}
