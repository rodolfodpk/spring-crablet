package com.crablet.outbox.config;

import com.crablet.outbox.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OutboxConfig.
 * Tests default values, getters/setters, and topic configuration.
 * 
 * Note: Tests for getTopics() method that requires TopicConfigurationProperties
 * are better covered in integration tests with Spring context.
 */
@DisplayName("OutboxConfig Unit Tests")
class OutboxConfigTest {

    @Test
    @DisplayName("Should have default values")
    void shouldHaveDefaultValues() {
        // When
        OutboxConfig config = new OutboxConfig();

        // Then
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getBatchSize()).isEqualTo(100);
        assertThat(config.getFetchSize()).isEqualTo(100);
        assertThat(config.getPollingIntervalMs()).isEqualTo(1000L);
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getRetryDelayMs()).isEqualTo(5000L);
        assertThat(config.isBackoffEnabled()).isTrue();
        assertThat(config.getBackoffThreshold()).isEqualTo(3);
        assertThat(config.getBackoffMultiplier()).isEqualTo(2);
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(60);
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("Should set and get enabled")
    void shouldSetAndGetEnabled() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setEnabled(true);

        // Then
        assertThat(config.isEnabled()).isTrue();

        // When
        config.setEnabled(false);

        // Then
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should set and get batchSize")
    void shouldSetAndGetBatchSize() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBatchSize(50);

        // Then
        assertThat(config.getBatchSize()).isEqualTo(50);

        // When
        config.setBatchSize(200);

        // Then
        assertThat(config.getBatchSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should set and get fetchSize")
    void shouldSetAndGetFetchSize() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setFetchSize(50);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(50);

        // When
        config.setFetchSize(200);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should set and get pollingIntervalMs")
    void shouldSetAndGetPollingIntervalMs() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setPollingIntervalMs(500L);

        // Then
        assertThat(config.getPollingIntervalMs()).isEqualTo(500L);

        // When
        config.setPollingIntervalMs(2000L);

        // Then
        assertThat(config.getPollingIntervalMs()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("Should set and get maxRetries")
    void shouldSetAndGetMaxRetries() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setMaxRetries(5);

        // Then
        assertThat(config.getMaxRetries()).isEqualTo(5);

        // When
        config.setMaxRetries(1);

        // Then
        assertThat(config.getMaxRetries()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should set and get retryDelayMs")
    void shouldSetAndGetRetryDelayMs() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setRetryDelayMs(10000L);

        // Then
        assertThat(config.getRetryDelayMs()).isEqualTo(10000L);

        // When
        config.setRetryDelayMs(1000L);

        // Then
        assertThat(config.getRetryDelayMs()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("Should set and get backoffEnabled")
    void shouldSetAndGetBackoffEnabled() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBackoffEnabled(false);

        // Then
        assertThat(config.isBackoffEnabled()).isFalse();

        // When
        config.setBackoffEnabled(true);

        // Then
        assertThat(config.isBackoffEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should set and get backoffThreshold")
    void shouldSetAndGetBackoffThreshold() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBackoffThreshold(5);

        // Then
        assertThat(config.getBackoffThreshold()).isEqualTo(5);

        // When
        config.setBackoffThreshold(1);

        // Then
        assertThat(config.getBackoffThreshold()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should set and get backoffMultiplier")
    void shouldSetAndGetBackoffMultiplier() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBackoffMultiplier(3);

        // Then
        assertThat(config.getBackoffMultiplier()).isEqualTo(3);

        // When
        config.setBackoffMultiplier(4);

        // Then
        assertThat(config.getBackoffMultiplier()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should set and get backoffMaxSeconds")
    void shouldSetAndGetBackoffMaxSeconds() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBackoffMaxSeconds(120);

        // Then
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(120);

        // When
        config.setBackoffMaxSeconds(30);

        // Then
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("Should set and get leaderElectionRetryIntervalMs")
    void shouldSetAndGetLeaderElectionRetryIntervalMs() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setLeaderElectionRetryIntervalMs(60000L);

        // Then
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(60000L);

        // When
        config.setLeaderElectionRetryIntervalMs(15000L);

        // Then
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("Should handle zero values")
    void shouldHandleZeroValues() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBatchSize(0);
        config.setFetchSize(0);
        config.setPollingIntervalMs(0L);
        config.setMaxRetries(0);
        config.setRetryDelayMs(0L);
        config.setBackoffThreshold(0);
        config.setBackoffMultiplier(0);
        config.setBackoffMaxSeconds(0);
        config.setLeaderElectionRetryIntervalMs(0L);

        // Then - No validation, so zero values are allowed
        assertThat(config.getBatchSize()).isEqualTo(0);
        assertThat(config.getFetchSize()).isEqualTo(0);
        assertThat(config.getPollingIntervalMs()).isEqualTo(0L);
        assertThat(config.getMaxRetries()).isEqualTo(0);
        assertThat(config.getRetryDelayMs()).isEqualTo(0L);
        assertThat(config.getBackoffThreshold()).isEqualTo(0);
        assertThat(config.getBackoffMultiplier()).isEqualTo(0);
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(0);
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle negative values")
    void shouldHandleNegativeValues() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBatchSize(-1);
        config.setFetchSize(-1);
        config.setPollingIntervalMs(-1L);
        config.setMaxRetries(-1);
        config.setRetryDelayMs(-1L);
        config.setBackoffThreshold(-1);
        config.setBackoffMultiplier(-1);
        config.setBackoffMaxSeconds(-1);
        config.setLeaderElectionRetryIntervalMs(-1L);

        // Then - No validation, so negative values are allowed
        assertThat(config.getBatchSize()).isEqualTo(-1);
        assertThat(config.getFetchSize()).isEqualTo(-1);
        assertThat(config.getPollingIntervalMs()).isEqualTo(-1L);
        assertThat(config.getMaxRetries()).isEqualTo(-1);
        assertThat(config.getRetryDelayMs()).isEqualTo(-1L);
        assertThat(config.getBackoffThreshold()).isEqualTo(-1);
        assertThat(config.getBackoffMultiplier()).isEqualTo(-1);
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(-1);
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Should handle very large values")
    void shouldHandleVeryLargeValues() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When
        config.setBatchSize(Integer.MAX_VALUE);
        config.setFetchSize(Integer.MAX_VALUE);
        config.setPollingIntervalMs(Long.MAX_VALUE);
        config.setMaxRetries(Integer.MAX_VALUE);
        config.setRetryDelayMs(Long.MAX_VALUE);
        config.setBackoffThreshold(Integer.MAX_VALUE);
        config.setBackoffMultiplier(Integer.MAX_VALUE);
        config.setBackoffMaxSeconds(Integer.MAX_VALUE);
        config.setLeaderElectionRetryIntervalMs(Long.MAX_VALUE);

        // Then
        assertThat(config.getBatchSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getFetchSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getPollingIntervalMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(config.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getRetryDelayMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(config.getBackoffThreshold()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getBackoffMultiplier()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getBackoffMaxSeconds()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("setTopics should be a no-op (topics configured via TopicConfigurationProperties)")
    void setTopics_ShouldBeNoOp() {
        // Given
        OutboxConfig config = new OutboxConfig();
        Map<String, TopicConfig> topics = Map.of(
            "topic1", TopicConfig.builder("topic1").build()
        );

        // When - Should not throw, but doesn't actually set topics
        config.setTopics(topics);

        // Then - getTopics() requires TopicConfigurationProperties (tested in integration tests)
        // This test just verifies setTopics() doesn't throw
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("Should allow chaining setter calls")
    void shouldAllowChainingSetterCalls() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When & Then - Setters don't return this, but can verify they work
        config.setEnabled(true);
        config.setBatchSize(50);
        config.setPollingIntervalMs(500L);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getBatchSize()).isEqualTo(50);
        assertThat(config.getPollingIntervalMs()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Should maintain state after multiple setter calls")
    void shouldMaintainState_AfterMultipleSetterCalls() {
        // Given
        OutboxConfig config = new OutboxConfig();

        // When - Multiple setter calls
        config.setEnabled(true);
        config.setBatchSize(150);
        config.setMaxRetries(5);
        config.setBackoffEnabled(false);
        config.setBackoffThreshold(10);
        config.setBackoffMultiplier(3);
        config.setLeaderElectionRetryIntervalMs(45000L);

        // Then
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getBatchSize()).isEqualTo(150);
        assertThat(config.getMaxRetries()).isEqualTo(5);
        assertThat(config.isBackoffEnabled()).isFalse();
        assertThat(config.getBackoffThreshold()).isEqualTo(10);
        assertThat(config.getBackoffMultiplier()).isEqualTo(3);
        assertThat(config.getLeaderElectionRetryIntervalMs()).isEqualTo(45000L);
    }
}

