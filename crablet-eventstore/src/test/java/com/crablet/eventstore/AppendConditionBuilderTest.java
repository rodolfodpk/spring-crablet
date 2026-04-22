package com.crablet.eventstore;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AppendConditionBuilder.
 * Tests builder pattern for creating AppendCondition with idempotency checks.
 */
@DisplayName("AppendConditionBuilder Unit Tests")
class AppendConditionBuilderTest {

    @Test
    @DisplayName("Should build AppendCondition with stream position and decision model")
    void shouldBuildAppendConditionWithStreamPositionAndDecisionModel() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .build();

        // Then
        assertThat(condition.afterPosition()).isEqualTo(streamPosition);
        assertThat(condition.concurrencyQuery()).isEqualTo(decisionModel);
        assertThat(condition.idempotencyQuery()).isEqualTo(Query.empty());
    }

    @Test
    @DisplayName("Should build AppendCondition with idempotency check using Tag array")
    void shouldBuildAppendConditionWithIdempotencyCheckUsingTags() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();
        Tag walletTag = new Tag("wallet_id", "wallet-123");

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", walletTag)
                .build();

        // Then
        assertThat(condition.afterPosition()).isEqualTo(streamPosition);
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
        assertThat(condition.idempotencyQuery().items()).hasSize(1);
    }

    @Test
    @DisplayName("Should build AppendCondition with idempotency check using convenience method")
    void shouldBuildAppendConditionWithIdempotencyCheckUsingConvenienceMethod() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "wallet-123")
                .build();

        // Then
        assertThat(condition.afterPosition()).isEqualTo(streamPosition);
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
        assertThat(condition.idempotencyQuery().items()).hasSize(1);
        assertThat(condition.idempotencyQuery().items().get(0).eventTypes()).contains("WalletOpened");
        assertThat(condition.idempotencyQuery().items().get(0).tags()).hasSize(1);
        assertThat(condition.idempotencyQuery().items().get(0).tags().get(0).key()).isEqualTo("wallet_id");
        assertThat(condition.idempotencyQuery().items().get(0).tags().get(0).value()).isEqualTo("wallet-123");
    }

    @Test
    @DisplayName("Should build AppendCondition with multiple idempotency checks")
    void shouldBuildAppendConditionWithMultipleIdempotencyChecks() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "wallet-123")
                .withIdempotencyCheck("DepositMade", "deposit_id", "deposit-456")
                .build();

        // Then
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
        assertThat(condition.idempotencyQuery().items()).hasSize(2);
    }

    @Test
    @DisplayName("Should build AppendCondition with idempotency check and stream position check")
    void shouldBuildAppendConditionWithBothChecks() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "wallet-123")
                .build();

        // Then
        assertThat(condition.afterPosition()).isEqualTo(streamPosition);
        assertThat(condition.concurrencyQuery()).isEqualTo(decisionModel);
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle null decisionModelQuery")
    void shouldHandleNullDecisionModelQuery() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When & Then - AppendConditionBuilder allows null (validation happens at build time)
        // Note: Actual implementation doesn't validate nulls in constructor
        assertThatThrownBy(() -> {
            AppendConditionBuilder builder = new AppendConditionBuilder(null, streamPosition);
            builder.build();
        }).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle null stream position")
    void shouldHandleNullStreamPosition() {
        // Given
        Query decisionModel = Query.empty();

        // When & Then - AppendConditionBuilder allows null (validation happens at build time)
        // Note: Actual implementation doesn't validate nulls in constructor
        assertThatThrownBy(() -> {
            AppendConditionBuilder builder = new AppendConditionBuilder(decisionModel, null);
            builder.build();
        }).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle idempotency check with null eventType")
    void shouldHandleIdempotencyCheckWithNullEventType() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When & Then - Null eventType should be handled (validation happens in AppendCondition.of)
        AppendConditionBuilder builder = new AppendConditionBuilder(decisionModel, streamPosition);
        
        // This will create the condition, but validation happens later
        assertThatThrownBy(() -> builder.withIdempotencyCheck(null, "wallet_id", "wallet-123"))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle idempotency check with empty eventType")
    void shouldHandleIdempotencyCheckWithEmptyEventType() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Empty string is allowed (validation happens in AppendCondition usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("", "wallet_id", "wallet-123")
                .build();

        // Then
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle idempotency check with null tagKey")
    void shouldHandleIdempotencyCheckWithNullTagKey() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Tag record allows null values (validation happens in usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", null, "wallet-123")
                .build();

        // Then - Condition is created, validation happens at usage time
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle idempotency check with null tagValue")
    void shouldHandleIdempotencyCheckWithNullTagValue() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Tag record allows null values (validation happens in usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", "wallet_id", null)
                .build();

        // Then - Condition is created, validation happens at usage time
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle idempotency check with empty tagValue")
    void shouldHandleIdempotencyCheckWithEmptyTagValue() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Empty tagValue is allowed (validation happens in AppendCondition usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "")
                .build();

        // Then
        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
    }

    @Test
    @DisplayName("Should combine decisionModelQuery with concurrency items")
    void shouldCombineDecisionModelQueryWithConcurrencyItems() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .build();

        // Then - concurrencyQuery should include decisionModel query
        assertThat(condition.concurrencyQuery()).isEqualTo(decisionModel);
    }

    @Test
    @DisplayName("Should build AppendCondition with non-empty decisionModelQuery")
    void shouldBuildAppendConditionWithNonEmptyDecisionModelQuery() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.of(
                QueryItem.of(List.of("WalletOpened", "DepositMade"), List.of())
        );

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .build();

        // Then - concurrencyQuery should include decisionModel query items
        assertThat(condition.concurrencyQuery()).isEqualTo(decisionModel);
        assertThat(condition.concurrencyQuery().items()).hasSize(1);
        assertThat(condition.concurrencyQuery().items().get(0).eventTypes()).contains("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should combine non-empty decisionModelQuery with idempotency check")
    void shouldCombineNonEmptyDecisionModelQueryWithIdempotencyCheck() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.of(
                QueryItem.of(List.of("WalletOpened"), List.of())
        );

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .withIdempotencyCheck("DepositMade", "deposit_id", "dep-123")
                .build();

        // Then - concurrencyQuery should preserve decisionModel, idempotencyQuery should have idempotency check
        assertThat(condition.concurrencyQuery()).isEqualTo(decisionModel);
        assertThat(condition.idempotencyQuery().items()).hasSize(1);
        assertThat(condition.idempotencyQuery().items().get(0).eventTypes()).contains("DepositMade");
    }

    @Test
    @DisplayName("Should build AppendCondition without idempotency check when not called")
    void shouldBuildAppendConditionWithoutIdempotencyCheck() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, streamPosition)
                .build();

        // Then — no idempotency check means Query.empty(), not null
        assertThat(condition.idempotencyQuery()).isEqualTo(Query.empty());
    }
}
