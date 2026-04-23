package com.crablet.outbox.config;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.internal.sharedfetch.SharedFetchModuleProcessor;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.outbox.TopicConfig;
import com.crablet.outbox.TopicPublisherPair;
import com.crablet.outbox.internal.OutboxProcessorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OutboxAutoConfiguration Unit Tests")
class OutboxAutoConfigurationTest {

    private final OutboxAutoConfiguration autoConfiguration = new OutboxAutoConfiguration();

    @Test
    @DisplayName("Should create shared-fetch outbox event processor")
    void shouldCreateSharedFetchOutboxEventProcessor() {
        OutboxConfig outboxConfig = new OutboxConfig();
        outboxConfig.setEnabled(true);
        TopicConfig topicConfig = TopicConfig.builder("wallet")
                .publishers("publisher-a")
                .build();
        TopicConfigurationProperties properties = new TopicConfigurationProperties();
        TopicPublisherPair pair = new TopicPublisherPair("wallet", "publisher-a");
        Map<TopicPublisherPair, OutboxProcessorConfig> configs = Map.of(
                pair, new OutboxProcessorConfig(pair, outboxConfig, topicConfig, properties));
        LeaderElector leaderElector = mock(LeaderElector.class);
        when(leaderElector.getInstanceId()).thenReturn("instance-1");

        EventProcessor<OutboxProcessorConfig, TopicPublisherPair> processor = autoConfiguration.outboxEventProcessorSharedFetch(
                configs,
                Map.of("wallet", topicConfig),
                leaderElector,
                mock(ProgressTracker.class),
                mock(EventHandler.class),
                outboxConfig,
                new WriteDataSource(mock(DataSource.class)),
                new ReadDataSource(mock(DataSource.class)),
                mock(ClockProvider.class),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class));

        assertThat(processor).isInstanceOf(SharedFetchModuleProcessor.class);
    }

    @Test
    @DisplayName("Should create shared-fetch outbox event processor when topic selection is missing")
    void shouldCreateSharedFetchOutboxEventProcessorWhenTopicSelectionIsMissing() {
        OutboxConfig outboxConfig = new OutboxConfig();
        TopicPublisherPair pair = new TopicPublisherPair("missing", "publisher-a");
        OutboxProcessorConfig config = new OutboxProcessorConfig(
                pair, outboxConfig, TopicConfig.builder("missing").publishers("publisher-a").build(),
                new TopicConfigurationProperties());
        LeaderElector leaderElector = mock(LeaderElector.class);
        when(leaderElector.getInstanceId()).thenReturn("instance-1");

        EventProcessor<OutboxProcessorConfig, TopicPublisherPair> processor = autoConfiguration.outboxEventProcessorSharedFetch(
                Map.of(pair, config),
                Map.of(),
                leaderElector,
                mock(ProgressTracker.class),
                mock(EventHandler.class),
                outboxConfig,
                new WriteDataSource(mock(DataSource.class)),
                new ReadDataSource(mock(DataSource.class)),
                mock(ClockProvider.class),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class));

        assertThat(processor).isInstanceOf(SharedFetchModuleProcessor.class);
    }
}
