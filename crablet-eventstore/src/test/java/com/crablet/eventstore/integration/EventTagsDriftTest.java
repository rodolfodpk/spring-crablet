package com.crablet.eventstore.integration;

import com.crablet.eventstore.AppendEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("event_tags drift tests")
class EventTagsDriftTest extends AbstractCrabletTest {

    @Test
    @DisplayName("every event with tags has a corresponding event_tags row")
    void noEventWithTagsIsMissingFromEventTags() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "w1")
                .tag("owner", "Alice")
                .data("{}")
                .build(),
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "w1")
                .tag("amount", "500")
                .data("{}")
                .build()
        ));

        Integer missing = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM crablet_events e
            WHERE cardinality(e.tags) > 0
              AND NOT EXISTS (
                  SELECT 1 FROM crablet_event_tags t WHERE t.position = e.position
              )
            """, Integer.class);

        assertThat(missing).isZero();
    }

    @Test
    @DisplayName("every event_tags row references an existing event")
    void noOrphanEventTagsRows() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "w2")
                .data("{}")
                .build()
        ));

        Integer orphans = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM crablet_event_tags t
            WHERE NOT EXISTS (
                SELECT 1 FROM crablet_events e WHERE e.position = t.position
            )
            """, Integer.class);

        assertThat(orphans).isZero();
    }

    @Test
    @DisplayName("tag count in event_tags matches cardinality of crablet_events.tags per position")
    void tagCountPerPositionMatches() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("MoneyTransferred")
                .tag("from_wallet_id", "w1")
                .tag("to_wallet_id", "w2")
                .tag("transfer_id", "t1")
                .data("{}")
                .build()
        ));

        Integer mismatched = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM crablet_events e
            JOIN (SELECT position, COUNT(*) AS tag_count FROM crablet_event_tags GROUP BY position) t
                ON t.position = e.position
            WHERE t.tag_count != cardinality(e.tags)
            """, Integer.class);

        assertThat(mismatched).isZero();
    }

    @Test
    @DisplayName("tag key and value are parsed correctly from crablet_events.tags encoding")
    void tagKeyValueParsedCorrectly() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "w=special")
                .data("{}")
                .build()
        ));

        var row = jdbcTemplate.queryForMap(
            "SELECT key, value FROM crablet_event_tags ORDER BY position DESC LIMIT 1");

        assertThat(row.get("key")).isEqualTo("wallet_id");
        assertThat(row.get("value")).isEqualTo("w=special");
    }
}
