package com.crablet.eventstore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Tag helper methods.
 * Tests DCB-compliant tag creation patterns.
 */
class TagTest {

    @Test
    void shouldCreateSingleTag() {
        // Given
        String key = "wallet_id";
        String value = "wallet-123";

        // When
        Tag tag = Tag.of(key, value);

        // Then
        assertThat(tag.key()).isEqualTo(key);
        assertThat(tag.value()).isEqualTo(value);
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
    void shouldNormalizeKeyToLowercase() {
        assertThat(new Tag("WALLET_ID", "abc").key()).isEqualTo("wallet_id");
        assertThat(Tag.of("WalletId", "abc").key()).isEqualTo("walletid");
        assertThat(Tag.of("DEPOSIT_ID", "d1").key()).isEqualTo("deposit_id");
    }

    @Test
    void shouldPreserveValueCaseSensitivity() {
        Tag tag = new Tag("wallet_id", "WalletABC-123");
        assertThat(tag.key()).isEqualTo("wallet_id");
        assertThat(tag.value()).isEqualTo("WalletABC-123");
    }

    @Test
    void shouldHandleNullKey() {
        Tag tag = new Tag(null, "value");
        assertThat(tag.key()).isNull();
        assertThat(tag.value()).isEqualTo("value");
    }
}
