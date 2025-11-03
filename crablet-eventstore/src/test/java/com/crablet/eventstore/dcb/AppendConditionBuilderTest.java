package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.SequenceNumber;
import com.crablet.eventstore.store.Tag;
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
    @DisplayName("Should build AppendCondition with cursor and decision model")
    void shouldBuildAppendConditionWithCursorAndDecisionModel() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .build();

        // Then
        assertThat(condition.afterCursor()).isEqualTo(cursor);
        assertThat(condition.stateChanged()).isEqualTo(decisionModel);
        assertThat(condition.alreadyExists()).isNull();
    }

    @Test
    @DisplayName("Should build AppendCondition with idempotency check using Tag array")
    void shouldBuildAppendConditionWithIdempotencyCheckUsingTags() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();
        Tag walletTag = new Tag("wallet_id", "wallet-123");

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", walletTag)
                .build();

        // Then
        assertThat(condition.afterCursor()).isEqualTo(cursor);
        assertThat(condition.alreadyExists()).isNotNull();
        assertThat(condition.alreadyExists().items()).hasSize(1);
    }

    @Test
    @DisplayName("Should build AppendCondition with idempotency check using convenience method")
    void shouldBuildAppendConditionWithIdempotencyCheckUsingConvenienceMethod() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "wallet-123")
                .build();

        // Then
        assertThat(condition.afterCursor()).isEqualTo(cursor);
        assertThat(condition.alreadyExists()).isNotNull();
        assertThat(condition.alreadyExists().items()).hasSize(1);
        assertThat(condition.alreadyExists().items().get(0).eventTypes()).contains("WalletOpened");
        assertThat(condition.alreadyExists().items().get(0).tags()).hasSize(1);
        assertThat(condition.alreadyExists().items().get(0).tags().get(0).key()).isEqualTo("wallet_id");
        assertThat(condition.alreadyExists().items().get(0).tags().get(0).value()).isEqualTo("wallet-123");
    }

    @Test
    @DisplayName("Should build AppendCondition with multiple idempotency checks")
    void shouldBuildAppendConditionWithMultipleIdempotencyChecks() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "wallet-123")
                .withIdempotencyCheck("DepositMade", "deposit_id", "deposit-456")
                .build();

        // Then
        assertThat(condition.alreadyExists()).isNotNull();
        assertThat(condition.alreadyExists().items()).hasSize(2);
    }

    @Test
    @DisplayName("Should build AppendCondition with idempotency check and cursor check")
    void shouldBuildAppendConditionWithBothChecks() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "wallet-123")
                .build();

        // Then
        assertThat(condition.afterCursor()).isEqualTo(cursor);
        assertThat(condition.stateChanged()).isEqualTo(decisionModel);
        assertThat(condition.alreadyExists()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null decisionModelQuery")
    void shouldHandleNullDecisionModelQuery() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When & Then - AppendConditionBuilder allows null (validation happens at build time)
        // Note: Actual implementation doesn't validate nulls in constructor
        assertThatThrownBy(() -> {
            AppendConditionBuilder builder = new AppendConditionBuilder(null, cursor);
            builder.build();
        }).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle null cursor")
    void shouldHandleNullCursor() {
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
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When & Then - Null eventType should be handled (validation happens in AppendCondition.of)
        AppendConditionBuilder builder = new AppendConditionBuilder(decisionModel, cursor);
        
        // This will create the condition, but validation happens later
        assertThatThrownBy(() -> builder.withIdempotencyCheck(null, "wallet_id", "wallet-123"))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle idempotency check with empty eventType")
    void shouldHandleIdempotencyCheckWithEmptyEventType() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Empty string is allowed (validation happens in AppendCondition usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("", "wallet_id", "wallet-123")
                .build();

        // Then
        assertThat(condition.alreadyExists()).isNotNull();
    }

    @Test
    @DisplayName("Should handle idempotency check with null tagKey")
    void shouldHandleIdempotencyCheckWithNullTagKey() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Tag record allows null values (validation happens in usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", null, "wallet-123")
                .build();

        // Then - Condition is created, validation happens at usage time
        assertThat(condition.alreadyExists()).isNotNull();
    }

    @Test
    @DisplayName("Should handle idempotency check with null tagValue")
    void shouldHandleIdempotencyCheckWithNullTagValue() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Tag record allows null values (validation happens in usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", "wallet_id", null)
                .build();

        // Then - Condition is created, validation happens at usage time
        assertThat(condition.alreadyExists()).isNotNull();
    }

    @Test
    @DisplayName("Should handle idempotency check with empty tagValue")
    void shouldHandleIdempotencyCheckWithEmptyTagValue() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When - Empty tagValue is allowed (validation happens in AppendCondition usage)
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WalletOpened", "wallet_id", "")
                .build();

        // Then
        assertThat(condition.alreadyExists()).isNotNull();
    }

    @Test
    @DisplayName("Should combine decisionModelQuery with concurrency items")
    void shouldCombineDecisionModelQueryWithConcurrencyItems() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .build();

        // Then - stateChanged should include decisionModel query
        assertThat(condition.stateChanged()).isEqualTo(decisionModel);
    }

    @Test
    @DisplayName("Should build AppendCondition with non-empty decisionModelQuery")
    void shouldBuildAppendConditionWithNonEmptyDecisionModelQuery() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.of(
                QueryItem.of(List.of("WalletOpened", "DepositMade"), List.of())
        );

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .build();

        // Then - stateChanged should include decisionModel query items
        assertThat(condition.stateChanged()).isEqualTo(decisionModel);
        assertThat(condition.stateChanged().items()).hasSize(1);
        assertThat(condition.stateChanged().items().get(0).eventTypes()).contains("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("Should combine non-empty decisionModelQuery with idempotency check")
    void shouldCombineNonEmptyDecisionModelQueryWithIdempotencyCheck() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.of(
                QueryItem.of(List.of("WalletOpened"), List.of())
        );

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("DepositMade", "deposit_id", "dep-123")
                .build();

        // Then - stateChanged should preserve decisionModel, alreadyExists should have idempotency check
        assertThat(condition.stateChanged()).isEqualTo(decisionModel);
        assertThat(condition.alreadyExists()).isNotNull();
        assertThat(condition.alreadyExists().items()).hasSize(1);
        assertThat(condition.alreadyExists().items().get(0).eventTypes()).contains("DepositMade");
    }

    @Test
    @DisplayName("Should build AppendCondition without idempotency check when not called")
    void shouldBuildAppendConditionWithoutIdempotencyCheck() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query decisionModel = Query.empty();

        // When
        AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .build();

        // Then
        assertThat(condition.alreadyExists()).isNull();
    }
}

