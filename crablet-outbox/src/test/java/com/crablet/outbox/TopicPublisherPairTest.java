package com.crablet.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TopicPublisherPair record.
 * Tests record constructor, toString(), getLockKey(), and equality/hashCode.
 */
@DisplayName("TopicPublisherPair Unit Tests")
class TopicPublisherPairTest {

    @Test
    @DisplayName("Should create TopicPublisherPair with topic and publisher")
    void shouldCreateTopicPublisherPair_WithTopicAndPublisher() {
        // Given
        String topic = "wallet-events";
        String publisher = "kafka-publisher";

        // When
        TopicPublisherPair pair = new TopicPublisherPair(topic, publisher);

        // Then
        assertThat(pair.topic()).isEqualTo(topic);
        assertThat(pair.publisher()).isEqualTo(publisher);
    }

    @Test
    @DisplayName("Should generate toString in format 'topic:publisher'")
    void shouldGenerateToString_InCorrectFormat() {
        // Given
        TopicPublisherPair pair = new TopicPublisherPair("wallet-events", "kafka-publisher");

        // When
        String toString = pair.toString();

        // Then
        assertThat(toString).isEqualTo("wallet-events:kafka-publisher");
    }

    @Test
    @DisplayName("Should generate consistent lock key for same topic and publisher")
    void shouldGenerateConsistentLockKey_ForSameTopicAndPublisher() {
        // Given
        TopicPublisherPair pair1 = new TopicPublisherPair("wallet-events", "kafka-publisher");
        TopicPublisherPair pair2 = new TopicPublisherPair("wallet-events", "kafka-publisher");

        // When
        long lockKey1 = pair1.getLockKey();
        long lockKey2 = pair2.getLockKey();

        // Then
        assertThat(lockKey1).isEqualTo(lockKey2);
    }

    @Test
    @DisplayName("Should generate different lock keys for different topic-publisher pairs")
    void shouldGenerateDifferentLockKeys_ForDifferentPairs() {
        // Given
        TopicPublisherPair pair1 = new TopicPublisherPair("wallet-events", "kafka-publisher");
        TopicPublisherPair pair2 = new TopicPublisherPair("wallet-events", "rabbit-publisher");
        TopicPublisherPair pair3 = new TopicPublisherPair("user-events", "kafka-publisher");

        // When
        long lockKey1 = pair1.getLockKey();
        long lockKey2 = pair2.getLockKey();
        long lockKey3 = pair3.getLockKey();

        // Then
        assertThat(lockKey1).isNotEqualTo(lockKey2);
        assertThat(lockKey1).isNotEqualTo(lockKey3);
        assertThat(lockKey2).isNotEqualTo(lockKey3);
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        TopicPublisherPair pair1 = new TopicPublisherPair("wallet-events", "kafka-publisher");
        TopicPublisherPair pair2 = new TopicPublisherPair("wallet-events", "kafka-publisher");

        // When & Then
        assertThat(pair1).isEqualTo(pair2);
        assertThat(pair1.hashCode()).isEqualTo(pair2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different topics")
    void shouldImplementEquals_WithDifferentTopics() {
        // Given
        TopicPublisherPair pair1 = new TopicPublisherPair("wallet-events", "kafka-publisher");
        TopicPublisherPair pair2 = new TopicPublisherPair("user-events", "kafka-publisher");

        // When & Then
        assertThat(pair1).isNotEqualTo(pair2);
    }

    @Test
    @DisplayName("Should implement equals with different publishers")
    void shouldImplementEquals_WithDifferentPublishers() {
        // Given
        TopicPublisherPair pair1 = new TopicPublisherPair("wallet-events", "kafka-publisher");
        TopicPublisherPair pair2 = new TopicPublisherPair("wallet-events", "rabbit-publisher");

        // When & Then
        assertThat(pair1).isNotEqualTo(pair2);
    }

    @Test
    @DisplayName("Should reject empty topic")
    void shouldRejectEmptyTopic() {
        assertThatThrownBy(() -> new TopicPublisherPair("", "kafka-publisher"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic");
    }

    @Test
    @DisplayName("Should reject empty publisher")
    void shouldRejectEmptyPublisher() {
        assertThatThrownBy(() -> new TopicPublisherPair("wallet-events", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Publisher");
    }

    @Test
    @SuppressWarnings("NullAway")
    @DisplayName("Should reject null topic")
    void shouldRejectNullTopic() {
        assertThatThrownBy(() -> new TopicPublisherPair(null, "kafka-publisher"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic");
    }

    @Test
    @SuppressWarnings("NullAway")
    @DisplayName("Should reject null publisher")
    void shouldRejectNullPublisher() {
        assertThatThrownBy(() -> new TopicPublisherPair("wallet-events", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Publisher");
    }

    @Test
    @DisplayName("Should handle special characters in topic and publisher names")
    void shouldHandleSpecialCharacters_InTopicAndPublisherNames() {
        // Given
        TopicPublisherPair pair = new TopicPublisherPair("topic-with-dashes", "publisher_with_underscores");

        // When & Then
        assertThat(pair.topic()).isEqualTo("topic-with-dashes");
        assertThat(pair.publisher()).isEqualTo("publisher_with_underscores");
        assertThat(pair.toString()).isEqualTo("topic-with-dashes:publisher_with_underscores");
        assertThat(pair.getLockKey()).isNotZero();
    }

    @Test
    @DisplayName("Should handle long topic and publisher names")
    void shouldHandleLongTopicAndPublisherNames() {
        // Given
        String longTopic = "a".repeat(100);
        String longPublisher = "b".repeat(100);
        TopicPublisherPair pair = new TopicPublisherPair(longTopic, longPublisher);

        // When & Then
        assertThat(pair.topic()).isEqualTo(longTopic);
        assertThat(pair.publisher()).isEqualTo(longPublisher);
        assertThat(pair.getLockKey()).isNotZero();
    }

    @Test
    @DisplayName("Should generate non-zero lock key")
    void shouldGenerateNonZeroLockKey() {
        // Given
        TopicPublisherPair pair = new TopicPublisherPair("wallet-events", "kafka-publisher");

        // When
        long lockKey = pair.getLockKey();

        // Then
        assertThat(lockKey).isNotZero();
    }

}

