package com.crablet.eventpoller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventSelectionSqlBuilder Unit Tests")
class EventSelectionSqlBuilderTest {

    @Test
    @DisplayName("Empty selection returns TRUE")
    void emptySelectionReturnsTrue() {
        String whereClause = EventSelectionSqlBuilder.buildWhereClause(selection(
                Set.of(), Set.of(), Set.of(), Map.of()));

        assertThat(whereClause).isEqualTo("TRUE");
    }

    @Test
    @DisplayName("Event type selection builds IN clause")
    void eventTypeSelectionBuildsInClause() {
        String whereClause = EventSelectionSqlBuilder.buildWhereClause(selection(
                Set.of("WalletOpened", "DepositMade"), Set.of(), Set.of(), Map.of()));

        assertThat(whereClause)
                .contains("type IN (")
                .contains("'WalletOpened'")
                .contains("'DepositMade'");
    }

    @Test
    @DisplayName("Required tags build event_tags correlated EXISTS clauses")
    void requiredTagsBuildExistsClauses() {
        String whereClause = EventSelectionSqlBuilder.buildWhereClause(selection(
                Set.of(), Set.of("wallet_id", "statement_id"), Set.of(), Map.of()));

        assertThat(whereClause)
                .contains("EXISTS (SELECT 1 FROM event_tags t WHERE t.position = events.position AND t.key = 'wallet_id')")
                .contains("EXISTS (SELECT 1 FROM event_tags t WHERE t.position = events.position AND t.key = 'statement_id')")
                .contains(" AND ");
    }

    @Test
    @DisplayName("Any-of tags build single event_tags IN EXISTS clause")
    void anyOfTagsBuildSingleOrExistsClause() {
        String whereClause = EventSelectionSqlBuilder.buildWhereClause(selection(
                Set.of(), Set.of(), Set.of("from_wallet_id", "to_wallet_id"), Map.of()));

        assertThat(whereClause)
                .contains("EXISTS (SELECT 1 FROM event_tags t WHERE t.position = events.position AND t.key IN (")
                .contains("'from_wallet_id'")
                .contains("'to_wallet_id'");
    }

    @Test
    @DisplayName("Exact tags build event_tags correlated EXISTS clauses")
    void exactTagsBuildAnyClauses() {
        String whereClause = EventSelectionSqlBuilder.buildWhereClause(selection(
                Set.of(), Set.of(), Set.of(), Map.of("wallet_id", "w1", "owner", "Alice")));

        assertThat(whereClause)
                .contains("EXISTS (SELECT 1 FROM event_tags t WHERE t.position = events.position AND t.key = 'wallet_id' AND t.value = 'w1')")
                .contains("EXISTS (SELECT 1 FROM event_tags t WHERE t.position = events.position AND t.key = 'owner' AND t.value = 'Alice')");
    }

    @Test
    @DisplayName("Combined selection joins all filters with AND")
    void combinedSelectionJoinsAllFiltersWithAnd() {
        String whereClause = EventSelectionSqlBuilder.buildWhereClause(selection(
                Set.of("MoneyTransferred"),
                Set.of("transfer_id"),
                Set.of("from_wallet_id", "to_wallet_id"),
                Map.of("currency", "USD")));

        assertThat(whereClause)
                .contains("type IN ('MoneyTransferred')")
                .contains("t.key = 'transfer_id'")
                .contains("t.key IN (")
                .contains("'from_wallet_id'")
                .contains("'to_wallet_id'")
                .contains("t.key = 'currency' AND t.value = 'USD'")
                .contains("t.position = events.position");
    }

    private static EventSelection selection(
            Set<String> types,
            Set<String> requiredTags,
            Set<String> anyOfTags,
            Map<String, String> exactTags) {
        return new EventSelection() {
            @Override
            public Set<String> getEventTypes() {
                return types;
            }

            @Override
            public Set<String> getRequiredTags() {
                return requiredTags;
            }

            @Override
            public Set<String> getAnyOfTags() {
                return anyOfTags;
            }

            @Override
            public Map<String, String> getExactTags() {
                return exactTags;
            }
        };
    }
}
