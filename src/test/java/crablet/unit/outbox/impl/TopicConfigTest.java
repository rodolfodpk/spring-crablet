package crablet.unit.outbox.impl;

import com.crablet.outbox.impl.TopicConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopicConfigTest {

    @Test
    void shouldMatchWhenAllRequiredTagsPresent() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTags("wallet_id", "user_id")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "wallet_id", "123",
            "user_id", "456",
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldNotMatchWhenRequiredTagsMissing() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTags("wallet_id", "user_id")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "wallet_id", "123"
            // Missing user_id
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void shouldMatchWhenAnyOfTagsPresent() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .anyOfTags("payment_id", "transfer_id")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "payment_id", "123",
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldNotMatchWhenAnyOfTagsMissing() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .anyOfTags("payment_id", "transfer_id")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "wallet_id", "123",
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void shouldMatchWhenExactTagsMatch() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .exactTag("event_type", "transfer")
            .exactTag("status", "completed")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "event_type", "transfer",
            "status", "completed",
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldNotMatchWhenExactTagsDontMatch() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .exactTag("event_type", "transfer")
            .exactTag("status", "completed")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "event_type", "deposit", // Wrong value
            "status", "completed",
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void shouldMatchWhenAllCriteriaMet() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTag("wallet_id")
            .anyOfTags("payment_id", "transfer_id")
            .exactTag("status", "completed")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "wallet_id", "123",        // Required
            "transfer_id", "456",      // AnyOf
            "status", "completed",     // Exact
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldNotMatchWhenAnyCriteriaNotMet() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTag("wallet_id")
            .anyOfTags("payment_id", "transfer_id")
            .exactTag("status", "completed")
            .build();
        
        Map<String, String> eventTags = Map.of(
            "wallet_id", "123",        // Required ✓
            "transfer_id", "456",      // AnyOf ✓
            "status", "pending",        // Exact ✗ (wrong value)
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void shouldMatchAllWhenNoCriteriaSpecified() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic").build();
        
        Map<String, String> eventTags = Map.of(
            "wallet_id", "123",
            "amount", "100"
        );

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldGenerateCorrectSqlFilterForRequiredTags() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTags("wallet_id", "user_id")
            .build();

        // When
        String sqlFilter = config.getSqlFilter();

        // Then
        assertThat(sqlFilter).contains("wallet_id:%");
        assertThat(sqlFilter).contains("user_id:%");
        assertThat(sqlFilter).contains("AND");
    }

    @Test
    void shouldGenerateCorrectSqlFilterForAnyOfTags() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .anyOfTags("payment_id", "transfer_id")
            .build();

        // When
        String sqlFilter = config.getSqlFilter();

        // Then
        assertThat(sqlFilter).contains("payment_id:%");
        assertThat(sqlFilter).contains("transfer_id:%");
        assertThat(sqlFilter).contains("OR");
    }

    @Test
    void shouldGenerateCorrectSqlFilterForExactTags() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .exactTag("status", "completed")
            .exactTag("type", "transfer")
            .build();

        // When
        String sqlFilter = config.getSqlFilter();

        // Then
        assertThat(sqlFilter).contains("status:completed");
        assertThat(sqlFilter).contains("type:transfer");
        assertThat(sqlFilter).contains("ANY(tags)");
    }

    @Test
    void shouldGenerateTrueWhenNoCriteria() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic").build();

        // When
        String sqlFilter = config.getSqlFilter();

        // Then
        assertThat(sqlFilter).isEqualTo("TRUE");
    }

    @Test
    void shouldGenerateCombinedSqlFilter() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTag("wallet_id")
            .anyOfTags("payment_id", "transfer_id")
            .exactTag("status", "completed")
            .build();

        // When
        String sqlFilter = config.getSqlFilter();

        // Then
        assertThat(sqlFilter).contains("wallet_id:%");
        assertThat(sqlFilter).contains("payment_id:%");
        assertThat(sqlFilter).contains("transfer_id:%");
        assertThat(sqlFilter).contains("status:completed");
        assertThat(sqlFilter).contains("AND");
    }

    @Test
    void shouldReturnCorrectName() {
        // Given
        TopicConfig config = TopicConfig.builder("wallet-events").build();

        // When
        String name = config.getName();

        // Then
        assertThat(name).isEqualTo("wallet-events");
    }

    @Test
    void shouldReturnCorrectPublishers() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .publishers("KafkaPublisher", "WebhookPublisher")
            .build();

        // When
        var publishers = config.getPublishers();

        // Then
        assertThat(publishers).containsExactlyInAnyOrder("KafkaPublisher", "WebhookPublisher");
    }

    @Test
    void shouldHandleBuilderPattern() {
        // Given & When
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTag("wallet_id")
            .anyOfTag("payment_id")
            .exactTag("status", "completed")
            .publisher("KafkaPublisher")
            .build();

        // Then
        assertThat(config.getName()).isEqualTo("test-topic");
        assertThat(config.getPublishers()).containsExactly("KafkaPublisher");
    }

    @Test
    void shouldMatchEmptyEventTagsWhenNoCriteria() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic").build();
        Map<String, String> eventTags = Map.of();

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldNotMatchEmptyEventTagsWhenCriteriaExist() {
        // Given
        TopicConfig config = TopicConfig.builder("test-topic")
            .requireTag("wallet_id")
            .build();
        Map<String, String> eventTags = Map.of();

        // When
        boolean matches = config.matches(eventTags);

        // Then
        assertThat(matches).isFalse();
    }
}
