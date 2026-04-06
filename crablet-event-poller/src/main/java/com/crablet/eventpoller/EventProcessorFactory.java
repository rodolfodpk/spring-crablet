package com.crablet.eventpoller;

import com.crablet.eventpoller.internal.EventProcessorImpl;
import com.crablet.eventpoller.internal.LeaderElectorImpl;
import com.crablet.eventpoller.internal.ProcessorManagementServiceImpl;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProgressTracker;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Factory for creating event processor infrastructure beans without directly
 * importing internal implementation classes.
 * <p>
 * Use this factory in {@code @Configuration} classes to avoid coupling to
 * {@code com.crablet.eventpoller.internal.*}.
 *
 * <pre>{@code
 * @Bean
 * public EventProcessor<MyConfig, String> myProcessor(
 *         Map<String, MyConfig> configs,
 *         ProgressTracker<String> progressTracker,
 *         EventFetcher<String> fetcher,
 *         EventHandler<String> handler,
 *         InstanceIdProvider instanceIdProvider,
 *         @Qualifier("primaryDataSource") DataSource primary,
 *         TaskScheduler scheduler,
 *         ApplicationEventPublisher publisher) {
 *     return EventProcessorFactory.createProcessor(
 *         configs, "my-processor", MY_LOCK_KEY, instanceIdProvider.getInstanceId(),
 *         progressTracker, fetcher, handler, primary, scheduler, publisher);
 * }
 * }</pre>
 */
public final class EventProcessorFactory {

    private EventProcessorFactory() {}

    /**
     * Creates a fully wired {@link EventProcessor} including its internal
     * {@code LeaderElector}. The leader elector is not exposed as a separate bean;
     * it is managed by the returned processor.
     *
     * @param configs          per-processor configuration map
     * @param processorName    human-readable name used in leader election and logs (e.g. "views")
     * @param lockKey          PostgreSQL advisory lock key — must be unique per processor type
     * @param instanceId       this instance's unique identifier (from {@link InstanceIdProvider})
     * @param progressTracker  tracks last-processed position per processor
     * @param eventFetcher     fetches events from the read replica
     * @param eventHandler     processes fetched event batches
     * @param primaryDataSource write datasource (used for lock acquisition and progress writes)
     * @param taskScheduler    Spring task scheduler for polling intervals
     * @param eventPublisher   publishes processing metrics
     */
    /**
     * Creates a {@link LeaderElector} for use with {@link #createProcessor(Map, LeaderElector, ProgressTracker, EventFetcher, EventHandler, DataSource, TaskScheduler, ApplicationEventPublisher)}.
     * Expose this as a separate bean when tests or management components need to inject the elector directly.
     */
    public static LeaderElector createLeaderElector(
            DataSource primaryDataSource,
            String processorName,
            String instanceId,
            long lockKey,
            ApplicationEventPublisher eventPublisher) {
        return new LeaderElectorImpl(primaryDataSource, processorName, instanceId, lockKey, eventPublisher);
    }

    /**
     * Creates a fully wired {@link EventProcessor} using an already-created {@link LeaderElector}.
     * Use this overload when the leader elector must also be a Spring bean (e.g. for test injection).
     */
    public static <C extends ProcessorConfig<I>, I> EventProcessor<C, I> createProcessor(
            Map<I, C> configs,
            LeaderElector leaderElector,
            ProgressTracker<I> progressTracker,
            EventFetcher<I> eventFetcher,
            EventHandler<I> eventHandler,
            DataSource primaryDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        return new EventProcessorImpl<>(
                configs, leaderElector, progressTracker, eventFetcher, eventHandler,
                primaryDataSource, taskScheduler, eventPublisher);
    }

    public static <C extends ProcessorConfig<I>, I> EventProcessor<C, I> createProcessor(
            Map<I, C> configs,
            String processorName,
            long lockKey,
            String instanceId,
            ProgressTracker<I> progressTracker,
            EventFetcher<I> eventFetcher,
            EventHandler<I> eventHandler,
            DataSource primaryDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {

        var leaderElector = new LeaderElectorImpl(
                primaryDataSource, processorName, instanceId, lockKey, eventPublisher);

        return new EventProcessorImpl<>(
                configs, leaderElector, progressTracker, eventFetcher, eventHandler,
                primaryDataSource, taskScheduler, eventPublisher);
    }

    /**
     * Creates a {@link ProcessorManagementService} for the given processor.
     *
     * @param eventProcessor  the processor to manage
     * @param progressTracker tracks last-processed position per processor
     * @param readDataSource  read datasource for status queries
     */
    public static <C extends ProcessorConfig<I>, I> ProcessorManagementService<I> createManagementService(
            EventProcessor<C, I> eventProcessor,
            ProgressTracker<I> progressTracker,
            DataSource readDataSource) {
        return new ProcessorManagementServiceImpl<>(eventProcessor, progressTracker, readDataSource);
    }
}
