package com.crablet.outbox.config;

import com.crablet.outbox.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TopicConfigurationProperties.
 * Tests getters/setters, toTopicConfigs() conversion logic, and inner classes.
 * 
 * Note: Tests for @PostConstruct logConfiguration() are better covered in integration tests.
 */
@DisplayName("TopicConfigurationProperties Unit Tests")
class TopicConfigurationPropertiesTest {

    @Test
    @DisplayName("Should initialize with empty topics map")
    void shouldInitialize_WithEmptyTopicsMap() {
        // When
        TopicConfigurationProperties props = new TopicConfigurationProperties();

        // Then
        assertThat(props.getTopics()).isEmpty();
    }

    @Test
    @DisplayName("Should set and get topics map")
    void shouldSetAndGetTopicsMap() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topics.put("wallet-events", topicProps);

        // When
        props.setTopics(topics);

        // Then
        assertThat(props.getTopics()).isEqualTo(topics);
        assertThat(props.getTopics().size()).isEqualTo(1);
        assertThat(props.getTopics().containsKey("wallet-events")).isTrue();
    }

    @Test
    @DisplayName("toTopicConfigs should convert empty map to empty result")
    void toTopicConfigs_WithEmptyMap_ShouldReturnEmptyMap() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        props.setTopics(new HashMap<>());

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        assertThat(configs).isEmpty();
    }

    @Test
    @DisplayName("toTopicConfigs should convert topic with requiredTags")
    void toTopicConfigs_WithRequiredTags_ShouldConvertCorrectly() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setRequiredTags("wallet,user");
        topics.put("wallet-events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        assertThat(configs).hasSize(1);
        TopicConfig config = configs.get("wallet-events");
        assertThat(config).isNotNull();
        assertThat(config.getName()).isEqualTo("wallet-events");
        assertThat(config.getRequiredTags()).containsExactlyInAnyOrder("wallet", "user");
    }

    @Test
    @DisplayName("toTopicConfigs should convert topic with anyOfTags")
    void toTopicConfigs_WithAnyOfTags_ShouldConvertCorrectly() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setAnyOfTags("priority,urgent");
        topics.put("alerts", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        assertThat(configs).hasSize(1);
        TopicConfig config = configs.get("alerts");
        assertThat(config).isNotNull();
        assertThat(config.getName()).isEqualTo("alerts");
        assertThat(config.getAnyOfTags()).containsExactlyInAnyOrder("priority", "urgent");
    }

    @Test
    @DisplayName("toTopicConfigs should convert topic with exactTags")
    void toTopicConfigs_WithExactTags_ShouldConvertCorrectly() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        Map<String, String> exactTags = Map.of("type", "transfer", "status", "pending");
        topicProps.setExactTags(exactTags);
        topics.put("transfers", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        assertThat(configs).hasSize(1);
        TopicConfig config = configs.get("transfers");
        assertThat(config).isNotNull();
        assertThat(config.getName()).isEqualTo("transfers");
        assertThat(config.getExactTags()).isEqualTo(exactTags);
    }

    @Test
    @DisplayName("toTopicConfigs should convert topic with publishers")
    void toTopicConfigs_WithPublishers_ShouldConvertCorrectly() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setPublishers("kafka-publisher,rabbit-publisher");
        topics.put("wallet-events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        assertThat(configs).hasSize(1);
        TopicConfig config = configs.get("wallet-events");
        assertThat(config).isNotNull();
        assertThat(config.getPublishers()).containsExactlyInAnyOrder("kafka-publisher", "rabbit-publisher");
    }

    @Test
    @DisplayName("toTopicConfigs should handle comma-separated values with whitespace")
    void toTopicConfigs_WithWhitespaceInCommaSeparated_ShouldTrim() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setRequiredTags(" wallet , user , account ");
        topicProps.setPublishers(" kafka-publisher , rabbit-publisher ");
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("events");
        assertThat(config.getRequiredTags()).containsExactlyInAnyOrder("wallet", "user", "account");
        assertThat(config.getPublishers()).containsExactlyInAnyOrder("kafka-publisher", "rabbit-publisher");
    }

    @Test
    @DisplayName("toTopicConfigs should handle empty/whitespace in comma-separated values")
    void toTopicConfigs_WithEmptyValuesInCommaSeparated_ShouldSkip() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setRequiredTags("wallet,,user,");
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then - Should skip empty values
        TopicConfig config = configs.get("events");
        assertThat(config.getRequiredTags()).containsExactlyInAnyOrder("wallet", "user");
    }

    @Test
    @DisplayName("toTopicConfigs should convert multiple topics")
    void toTopicConfigs_WithMultipleTopics_ShouldConvertAll() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        
        TopicConfigurationProperties.TopicProperties topic1 = new TopicConfigurationProperties.TopicProperties();
        topic1.setRequiredTags("wallet");
        topics.put("wallet-events", topic1);
        
        TopicConfigurationProperties.TopicProperties topic2 = new TopicConfigurationProperties.TopicProperties();
        topic2.setRequiredTags("user");
        topics.put("user-events", topic2);
        
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        assertThat(configs).hasSize(2);
        assertThat(configs.containsKey("wallet-events")).isTrue();
        assertThat(configs.containsKey("user-events")).isTrue();
        assertThat(configs.get("wallet-events").getRequiredTags()).contains("wallet");
        assertThat(configs.get("user-events").getRequiredTags()).contains("user");
    }

    @Test
    @DisplayName("toTopicConfigs should handle null requiredTags")
    void toTopicConfigs_WithNullRequiredTags_ShouldHandleGracefully() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setRequiredTags(null);
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("events");
        assertThat(config).isNotNull();
        assertThat(config.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("toTopicConfigs should handle empty requiredTags string")
    void toTopicConfigs_WithEmptyRequiredTags_ShouldHandleGracefully() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setRequiredTags("");
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("events");
        assertThat(config).isNotNull();
        assertThat(config.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("toTopicConfigs should handle null anyOfTags")
    void toTopicConfigs_WithNullAnyOfTags_ShouldHandleGracefully() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setAnyOfTags(null);
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("events");
        assertThat(config).isNotNull();
        assertThat(config.getAnyOfTags()).isEmpty();
    }

    @Test
    @DisplayName("toTopicConfigs should handle null exactTags")
    void toTopicConfigs_WithNullExactTags_ShouldHandleGracefully() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setExactTags(null);
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("events");
        assertThat(config).isNotNull();
        assertThat(config.getExactTags()).isEmpty();
    }

    @Test
    @DisplayName("toTopicConfigs should handle null publishers")
    void toTopicConfigs_WithNullPublishers_ShouldHandleGracefully() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setPublishers(null);
        topics.put("events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("events");
        assertThat(config).isNotNull();
        assertThat(config.getPublishers()).isEmpty();
    }

    @Test
    @DisplayName("toTopicConfigs should combine all properties")
    void toTopicConfigs_WithAllProperties_ShouldCombineCorrectly() {
        // Given
        TopicConfigurationProperties props = new TopicConfigurationProperties();
        Map<String, TopicConfigurationProperties.TopicProperties> topics = new HashMap<>();
        TopicConfigurationProperties.TopicProperties topicProps = new TopicConfigurationProperties.TopicProperties();
        topicProps.setRequiredTags("wallet,user");
        topicProps.setAnyOfTags("priority,urgent");
        topicProps.setExactTags(Map.of("type", "transfer"));
        topicProps.setPublishers("kafka-publisher,rabbit-publisher");
        topics.put("wallet-events", topicProps);
        props.setTopics(topics);

        // When
        Map<String, TopicConfig> configs = props.toTopicConfigs();

        // Then
        TopicConfig config = configs.get("wallet-events");
        assertThat(config.getRequiredTags()).containsExactlyInAnyOrder("wallet", "user");
        assertThat(config.getAnyOfTags()).containsExactlyInAnyOrder("priority", "urgent");
        assertThat(config.getExactTags()).containsEntry("type", "transfer");
        assertThat(config.getPublishers()).containsExactlyInAnyOrder("kafka-publisher", "rabbit-publisher");
    }

    @Test
    @DisplayName("TopicProperties should set and get requiredTags")
    void topicProperties_ShouldSetAndGetRequiredTags() {
        // Given
        TopicConfigurationProperties.TopicProperties props = new TopicConfigurationProperties.TopicProperties();

        // When
        props.setRequiredTags("wallet,user");

        // Then
        assertThat(props.getRequiredTags()).isEqualTo("wallet,user");
    }

    @Test
    @DisplayName("TopicProperties should set and get anyOfTags")
    void topicProperties_ShouldSetAndGetAnyOfTags() {
        // Given
        TopicConfigurationProperties.TopicProperties props = new TopicConfigurationProperties.TopicProperties();

        // When
        props.setAnyOfTags("priority,urgent");

        // Then
        assertThat(props.getAnyOfTags()).isEqualTo("priority,urgent");
    }

    @Test
    @DisplayName("TopicProperties should set and get exactTags")
    void topicProperties_ShouldSetAndGetExactTags() {
        // Given
        TopicConfigurationProperties.TopicProperties props = new TopicConfigurationProperties.TopicProperties();
        Map<String, String> exactTags = Map.of("type", "transfer");

        // When
        props.setExactTags(exactTags);

        // Then
        assertThat(props.getExactTags()).isEqualTo(exactTags);
    }

    @Test
    @DisplayName("TopicProperties should set and get publishers")
    void topicProperties_ShouldSetAndGetPublishers() {
        // Given
        TopicConfigurationProperties.TopicProperties props = new TopicConfigurationProperties.TopicProperties();

        // When
        props.setPublishers("kafka-publisher,rabbit-publisher");

        // Then
        assertThat(props.getPublishers()).isEqualTo("kafka-publisher,rabbit-publisher");
    }

    @Test
    @DisplayName("TopicProperties should set and get publisherConfigs")
    void topicProperties_ShouldSetAndGetPublisherConfigs() {
        // Given
        TopicConfigurationProperties.TopicProperties props = new TopicConfigurationProperties.TopicProperties();
        TopicConfigurationProperties.PublisherProperties pubProps = new TopicConfigurationProperties.PublisherProperties();
        pubProps.setName("kafka-publisher");
        pubProps.setPollingIntervalMs(500L);

        // When
        props.setPublisherConfigs(java.util.List.of(pubProps));

        // Then
        assertThat(props.getPublisherConfigs()).hasSize(1);
        assertThat(props.getPublisherConfigs().get(0).getName()).isEqualTo("kafka-publisher");
    }

    @Test
    @DisplayName("PublisherProperties should set and get name")
    void publisherProperties_ShouldSetAndGetName() {
        // Given
        TopicConfigurationProperties.PublisherProperties props = new TopicConfigurationProperties.PublisherProperties();

        // When
        props.setName("kafka-publisher");

        // Then
        assertThat(props.getName()).isEqualTo("kafka-publisher");
    }

    @Test
    @DisplayName("PublisherProperties should set and get pollingIntervalMs")
    void publisherProperties_ShouldSetAndGetPollingIntervalMs() {
        // Given
        TopicConfigurationProperties.PublisherProperties props = new TopicConfigurationProperties.PublisherProperties();

        // When
        props.setPollingIntervalMs(500L);

        // Then
        assertThat(props.getPollingIntervalMs()).isEqualTo(500L);
    }

    @Test
    @DisplayName("PublisherProperties should handle null pollingIntervalMs")
    void publisherProperties_ShouldHandleNullPollingIntervalMs() {
        // Given
        TopicConfigurationProperties.PublisherProperties props = new TopicConfigurationProperties.PublisherProperties();

        // When
        props.setPollingIntervalMs(null);

        // Then
        assertThat(props.getPollingIntervalMs()).isNull();
    }
}

