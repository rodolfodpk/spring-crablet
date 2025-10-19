package crablet.unit.core;

import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Query helper methods.
 * Tests DCB-compliant query creation patterns.
 */
class QueryTest {

    @Test
    void shouldCreateQueryForEventAndTag() {
        // Given
        String eventType = "WalletOpened";
        String tagKey = "wallet_id";
        String tagValue = "wallet-123";

        // When
        Query query = Query.forEventAndTag(eventType, tagKey, tagValue);

        // Then
        assertThat(query.items()).hasSize(1);
        QueryItem item = query.items().get(0);
        assertThat(item.eventTypes()).containsExactly(eventType);
        assertThat(item.tags()).containsExactly(new Tag(tagKey, tagValue));
    }

    @Test
    void shouldCreateQueryForEventAndTags() {
        // Given
        String eventType = "DepositMade";
        List<Tag> tags = List.of(
                new Tag("wallet_id", "wallet-123"),
                new Tag("deposit_id", "deposit-456")
        );

        // When
        Query query = Query.forEventAndTags(eventType, tags);

        // Then
        assertThat(query.items()).hasSize(1);
        QueryItem item = query.items().get(0);
        assertThat(item.eventTypes()).containsExactly(eventType);
        assertThat(item.tags()).containsExactlyElementsOf(tags);
    }

    @Test
    void shouldCreateQueryForEventOnly() {
        // Given
        String eventType = "WalletOpened";

        // When
        Query query = Query.forEvent(eventType);

        // Then
        assertThat(query.items()).hasSize(1);
        QueryItem item = query.items().get(0);
        assertThat(item.eventTypes()).containsExactly(eventType);
        assertThat(item.tags()).isEmpty();
    }

    @Test
    void shouldCreateQueryForEventsAndTags() {
        // Given
        List<String> eventTypes = List.of("WalletOpened", "DepositMade");
        List<Tag> tags = List.of(
                new Tag("wallet_id", "wallet-123"),
                new Tag("deposit_id", "deposit-456")
        );

        // When
        Query query = Query.forEventsAndTags(eventTypes, tags);

        // Then
        assertThat(query.items()).hasSize(1);
        QueryItem item = query.items().get(0);
        assertThat(item.eventTypes()).containsExactlyElementsOf(eventTypes);
        assertThat(item.tags()).containsExactlyElementsOf(tags);
    }

    @Test
    void shouldSupportDCBQueryPatterns() {
        // Test common DCB query patterns used in command handlers

        // Pattern 1: Check if wallet exists
        Query walletExistsQuery = Query.forEventAndTag("WalletOpened", "wallet_id", "wallet-123");
        assertThat(walletExistsQuery.items()).hasSize(1);

        // Pattern 2: Check if deposit was already processed (idempotency)
        Query depositProcessedQuery = Query.forEventAndTag("DepositMade", "deposit_id", "deposit-456");
        assertThat(depositProcessedQuery.items()).hasSize(1);

        // Pattern 3: Get all events for a wallet (no event type filter)
        Query allWalletEventsQuery = Query.forEventAndTag("", "wallet_id", "wallet-123");
        assertThat(allWalletEventsQuery.items()).hasSize(1);
    }

    @Test
    void shouldCreateEmptyQuery() {
        // When
        Query query = Query.empty();

        // Then
        assertThat(query.items()).isEmpty();
        assertThat(query.isEmpty()).isTrue();
    }

    @Test
    void shouldCreateQueryFromSingleItem() {
        // Given
        QueryItem item = QueryItem.ofType("WalletOpened");

        // When
        Query query = Query.of(item);

        // Then
        assertThat(query.items()).hasSize(1);
        assertThat(query.items().get(0)).isEqualTo(item);
    }

    @Test
    void shouldCreateQueryFromMultipleItems() {
        // Given
        List<QueryItem> items = List.of(
                QueryItem.ofType("WalletOpened"),
                QueryItem.ofType("DepositMade")
        );

        // When
        Query query = Query.of(items);

        // Then
        assertThat(query.items()).hasSize(2);
        assertThat(query.items()).containsExactlyElementsOf(items);
    }

    @Test
    void shouldSupportDCBAppendConditionPatterns() {
        // Test that Query helpers work well with DCB AppendCondition patterns
        // This is the pattern used in command handlers for idempotency checks

        // Given - typical idempotency check query
        Query idempotencyQuery = Query.forEventAndTag("DepositMade", "deposit_id", "deposit-123");

        // When - used in AppendCondition context
        // AppendCondition.of(cursor, idempotencyQuery)

        // Then - should be a valid query for DCB consistency checking
        assertThat(idempotencyQuery.items()).hasSize(1);
        QueryItem item = idempotencyQuery.items().get(0);
        assertThat(item.eventTypes()).containsExactly("DepositMade");
        assertThat(item.tags()).containsExactly(new Tag("deposit_id", "deposit-123"));
    }
}
