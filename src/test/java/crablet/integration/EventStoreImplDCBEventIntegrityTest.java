package crablet.integration;
import static crablet.testutils.DCBTestHelpers.*;

import com.crablet.core.AppendEvent;
import com.crablet.core.impl.EventStoreConfig;
import com.crablet.core.Query;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.crablet.core.impl.EventStoreImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crablet.core.ClockProvider;
import com.crablet.core.QuerySqlBuilder;
import com.crablet.core.EventTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static crablet.testutils.DCBTestHelpers.createTestEvent;

/**
 * Tests for DCB event data integrity.
 * Verifies that event type, tags, and JSON data are preserved exactly.
 */
class EventStoreImplDCBEventIntegrityTest extends AbstractCrabletIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventStoreConfig config;
    
    @Autowired
    private ClockProvider clock;

    @Autowired
    private QuerySqlBuilder sqlBuilder;

    @Autowired
    private EventTestHelper testHelper;

    private EventStoreImpl store;

    @BeforeEach
    void setUp() {
        store = new EventStoreImpl(dataSource, objectMapper, config, clock, sqlBuilder);
    }

    @Test
    void shouldPreserveEventType() {
        AppendEvent event = createTestEvent("MyEventType", "id1");
        store.append(List.of(event));

        List<StoredEvent> stored = testHelper.query(Query.empty(), null);
        assertThat(stored.get(0).type()).isEqualTo("MyEventType");
    }

    @Test
    void shouldPreserveEventTags() {
        AppendEvent event = AppendEvent.builder("TestEvent")
                .tag("key1", "value1")
                .tag("key2", "value2")
                .tag("key3", "value3")
                .data("{}")
                .build();
        store.append(List.of(event));

        List<StoredEvent> stored = testHelper.query(Query.empty(), null);
        assertThat(stored.get(0).tags())
                .containsExactlyInAnyOrder(
                        new Tag("key1", "value1"),
                        new Tag("key2", "value2"),
                        new Tag("key3", "value3")
                );
    }

    @Test
    void shouldPreserveComplexJsonData() throws Exception {
        String complexJson = """
                {
                    "id": "test-123",
                    "nested": {
                        "field1": "value1",
                        "field2": 42,
                        "field3": true
                    },
                    "array": [1, 2, 3],
                    "null_field": null
                }
                """;

        AppendEvent event = createTestEvent("TestEvent", complexJson.getBytes());
        store.append(List.of(event));

        List<StoredEvent> stored = testHelper.query(Query.empty(), null);
        String storedJson = new String(stored.get(0).data());

        // Parse and compare (ignoring whitespace)
        JsonNode expected = objectMapper.readTree(complexJson);
        JsonNode actual = objectMapper.readTree(storedJson);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldPreserveUnicodeAndSpecialCharacters() {
        String unicodeJson = """
                {
                    "emoji": "🚀💰",
                    "chinese": "中文",
                    "special": "\\n\\t\\r",
                    "quotes": "\\"nested\\" 'quotes'"
                }
                """;

        AppendEvent event = createTestEvent("UnicodeEvent", unicodeJson.getBytes(StandardCharsets.UTF_8));
        store.append(List.of(event));

        List<StoredEvent> stored = testHelper.query(Query.empty(), null);
        String storedJson = new String(stored.get(0).data(), StandardCharsets.UTF_8);

        // Verify the JSON is parseable and contains expected data
        try {
            JsonNode node = objectMapper.readTree(storedJson);
            assertThat(node.get("emoji").asText()).isEqualTo("🚀💰");
            assertThat(node.get("chinese").asText()).isEqualTo("中文");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse stored JSON", e);
        }
    }

    @Test
    void shouldPreserveTransactionIdAndTimestamp() {
        AppendEvent event = createTestEvent("TestEvent", "id1");
        Instant beforeAppend = Instant.now().minusSeconds(1); // Allow 1 second clock skew
        store.append(List.of(event));
        Instant afterAppend = Instant.now().plusSeconds(1);   // Allow 1 second clock skew

        List<StoredEvent> stored = testHelper.query(Query.empty(), null);
        StoredEvent storedEvent = stored.get(0);

        // Verify transaction_id is set (not null/zero)
        assertThat(storedEvent.transactionId()).isNotNull();
        assertThat(storedEvent.transactionId()).isNotEqualTo("0");

        // Verify timestamp is within expected range (allowing for clock skew)
        assertThat(storedEvent.occurredAt())
                .isAfterOrEqualTo(beforeAppend)
                .isBeforeOrEqualTo(afterAppend);
    }
}

