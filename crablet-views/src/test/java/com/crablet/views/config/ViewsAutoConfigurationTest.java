package com.crablet.views.config;

import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.internal.sharedfetch.SharedFetchModuleProcessor;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.views.ViewSubscription;
import com.crablet.views.internal.ViewProcessorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ViewsAutoConfiguration Unit Tests")
class ViewsAutoConfigurationTest {

    private final ViewsAutoConfiguration autoConfiguration = new ViewsAutoConfiguration();

    @Test
    @DisplayName("Should create shared-fetch views event processor")
    void shouldCreateSharedFetchViewsEventProcessor() {
        ViewsConfig config = new ViewsConfig();
        config.setFetchBatchSize(50);
        ViewSubscription subscription = ViewSubscription.builder("wallet-view")
                .eventTypes("WalletOpened")
                .build();
        Map<String, ViewProcessorConfig> processorConfigs = Map.of(
                "wallet-view", new ViewProcessorConfig("wallet-view", config, subscription));
        InstanceIdProvider instanceIdProvider = mock(InstanceIdProvider.class);
        when(instanceIdProvider.getInstanceId()).thenReturn("instance-1");

        EventProcessor<ViewProcessorConfig, String> processor = autoConfiguration.viewsEventProcessorSharedFetch(
                processorConfigs,
                Map.of("wallet-view", subscription),
                mock(ProgressTracker.class),
                mock(EventHandler.class),
                instanceIdProvider,
                config,
                new WriteDataSource(mock(DataSource.class)),
                new ReadDataSource(mock(DataSource.class)),
                mock(TaskScheduler.class),
                mock(ApplicationEventPublisher.class));

        assertThat(processor).isInstanceOf(SharedFetchModuleProcessor.class);
    }
}
