package com.crablet.outbox.processor;

import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.leader.OutboxLeaderElector;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import com.crablet.outbox.publishing.OutboxPublishingService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OutboxProcessorImpl cooldown mechanism and leader retry logic.
 * Uses reflection to test private methods and verify cooldown behavior.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxProcessorImplCooldownTest {

    @Mock
    private OutboxConfig config;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource readDataSource;

    @Mock
    private OutboxLeaderElector leaderElector;

    @Mock
    private OutboxPublishingService publishingService;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private GlobalStatisticsPublisher globalStatistics;

    @Mock
    private TopicConfigurationProperties topicConfigProperties;

    @Mock
    private TaskScheduler taskScheduler;

    private OutboxProcessorImpl processor;

    @BeforeEach
    void setUp() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getLeaderElectionRetryIntervalMs()).thenReturn(30000L);
        when(config.getTopics()).thenReturn(Map.of());
        when(topicConfigProperties.getTopics()).thenReturn(Map.of());

        processor = new OutboxProcessorImpl(
            config,
            jdbcTemplate,
            readDataSource,
            List.of(),
            leaderElector,
            publishingService,
            circuitBreakerRegistry,
            globalStatistics,
            topicConfigProperties,
            taskScheduler
        );
    }

    @Test
    void shouldRespectCooldownForRedundantRetries() throws Exception {
        // Given: Not leader
        when(leaderElector.isGlobalLeader()).thenReturn(false);
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(false);

        // Use reflection to access private scheduledTask method
        Method scheduledTask = OutboxProcessorImpl.class.getDeclaredMethod(
            "scheduledTask", String.class, String.class
        );
        scheduledTask.setAccessible(true);

        // Access lastLeaderRetryTimestamp field
        Field lastRetryField = OutboxProcessorImpl.class.getDeclaredField("lastLeaderRetryTimestamp");
        lastRetryField.setAccessible(true);

        // First call: Should attempt lock acquisition
        long firstCallTime = System.currentTimeMillis();
        scheduledTask.invoke(processor, "topic1", "publisher1");
        
        // Verify lock acquisition was attempted
        verify(leaderElector, times(1)).tryAcquireGlobalLeader();
        
        // Verify timestamp was updated
        long timestampAfterFirst = (Long) lastRetryField.get(processor);
        assertThat(timestampAfterFirst).isGreaterThanOrEqualTo(firstCallTime);

        // Reset mocks
        reset(leaderElector);
        when(leaderElector.isGlobalLeader()).thenReturn(false);
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(false);

        // Second call immediately: Should NOT retry (cooldown active)
        scheduledTask.invoke(processor, "topic1", "publisher1");
        
        // Verify lock acquisition was NOT attempted again (cooldown)
        verify(leaderElector, never()).tryAcquireGlobalLeader();
    }

    @Test
    void shouldRetryAfterCooldownPeriod() throws Exception {
        // Given: Not leader
        when(leaderElector.isGlobalLeader()).thenReturn(false);
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(false);

        Method scheduledTask = OutboxProcessorImpl.class.getDeclaredMethod(
            "scheduledTask", String.class, String.class
        );
        scheduledTask.setAccessible(true);

        Field lastRetryField = OutboxProcessorImpl.class.getDeclaredField("lastLeaderRetryTimestamp");
        lastRetryField.setAccessible(true);

        Field cooldownField = OutboxProcessorImpl.class.getDeclaredField("LEADER_RETRY_COOLDOWN_MS");
        cooldownField.setAccessible(true);
        long cooldownMs = cooldownField.getLong(null);

        // First call
        scheduledTask.invoke(processor, "topic1", "publisher1");
        verify(leaderElector, times(1)).tryAcquireGlobalLeader();

        // Reset mocks
        reset(leaderElector);
        when(leaderElector.isGlobalLeader()).thenReturn(false);
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(false);

        // Simulate cooldown has passed by updating timestamp
        long oldTimestamp = System.currentTimeMillis() - cooldownMs - 1000; // 1 second past cooldown
        lastRetryField.set(processor, oldTimestamp);

        // Second call after cooldown: Should retry
        scheduledTask.invoke(processor, "topic1", "publisher1");
        
        // Verify lock acquisition was attempted again
        verify(leaderElector, times(1)).tryAcquireGlobalLeader();
    }

    @Test
    void shouldProcessWhenLeaderAcquiresLockAfterRetry() throws Exception {
        // Given: Not leader initially, but acquires lock on retry
        when(leaderElector.isGlobalLeader())
            .thenReturn(false)  // First check: not leader
            .thenReturn(true);    // After retry: becomes leader
        
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(true);
        when(publishingService.publishForTopicPublisher(anyString(), anyString())).thenReturn(5);

        Method scheduledTask = OutboxProcessorImpl.class.getDeclaredMethod(
            "scheduledTask", String.class, String.class
        );
        scheduledTask.setAccessible(true);

        // When: scheduledTask is called
        scheduledTask.invoke(processor, "topic1", "publisher1");

        // Then: Should acquire lock and process events
        verify(leaderElector, times(1)).tryAcquireGlobalLeader();
        verify(publishingService, times(1)).publishForTopicPublisher("topic1", "publisher1");
    }

    @Test
    void shouldNotProcessWhenNotLeaderAndCooldownActive() throws Exception {
        // Given: Not leader and cooldown is active
        when(leaderElector.isGlobalLeader()).thenReturn(false);
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(false);

        Method scheduledTask = OutboxProcessorImpl.class.getDeclaredMethod(
            "scheduledTask", String.class, String.class
        );
        scheduledTask.setAccessible(true);

        Field lastRetryField = OutboxProcessorImpl.class.getDeclaredField("lastLeaderRetryTimestamp");
        lastRetryField.setAccessible(true);
        
        // Set recent timestamp (cooldown active)
        lastRetryField.set(processor, System.currentTimeMillis());

        // When: scheduledTask is called
        scheduledTask.invoke(processor, "topic1", "publisher1");

        // Then: Should NOT process events
        verify(leaderElector, never()).tryAcquireGlobalLeader();
        verify(publishingService, never()).publishForTopicPublisher(anyString(), anyString());
    }

    @Test
    void shouldSkipProcessingWhenNotLeaderAndLockAcquisitionFails() throws Exception {
        // Given: Not leader and lock acquisition fails
        when(leaderElector.isGlobalLeader()).thenReturn(false);
        when(leaderElector.tryAcquireGlobalLeader()).thenReturn(false);

        Method scheduledTask = OutboxProcessorImpl.class.getDeclaredMethod(
            "scheduledTask", String.class, String.class
        );
        scheduledTask.setAccessible(true);

        Field lastRetryField = OutboxProcessorImpl.class.getDeclaredField("lastLeaderRetryTimestamp");
        lastRetryField.setAccessible(true);
        
        // Set old timestamp (cooldown expired)
        Field cooldownField = OutboxProcessorImpl.class.getDeclaredField("LEADER_RETRY_COOLDOWN_MS");
        cooldownField.setAccessible(true);
        long cooldownMs = cooldownField.getLong(null);
        lastRetryField.set(processor, System.currentTimeMillis() - cooldownMs - 1000);

        // When: scheduledTask is called
        scheduledTask.invoke(processor, "topic1", "publisher1");

        // Then: Should attempt lock but NOT process (still not leader)
        verify(leaderElector, times(1)).tryAcquireGlobalLeader();
        verify(publishingService, never()).publishForTopicPublisher(anyString(), anyString());
    }

    @Test
    void shouldProcessNormallyWhenAlreadyLeader() throws Exception {
        // Given: Already leader
        when(leaderElector.isGlobalLeader()).thenReturn(true);
        when(publishingService.publishForTopicPublisher(anyString(), anyString())).thenReturn(3);

        Method scheduledTask = OutboxProcessorImpl.class.getDeclaredMethod(
            "scheduledTask", String.class, String.class
        );
        scheduledTask.setAccessible(true);

        // When: scheduledTask is called
        scheduledTask.invoke(processor, "topic1", "publisher1");

        // Then: Should process events without retrying lock
        verify(leaderElector, never()).tryAcquireGlobalLeader();
        verify(publishingService, times(1)).publishForTopicPublisher("topic1", "publisher1");
    }
}

