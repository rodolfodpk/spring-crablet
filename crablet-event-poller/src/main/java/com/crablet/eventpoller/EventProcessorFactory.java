package com.crablet.eventpoller;

import com.crablet.eventpoller.internal.EventProcessorImpl;
import com.crablet.eventpoller.internal.LeaderElectorImpl;
import com.crablet.eventpoller.internal.ProcessorManagementServiceImpl;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.management.ProcessorManagementService;
import com.crablet.eventpoller.processor.EventProcessor;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Objects.requireNonNull;

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
 *         WriteDataSource writeDataSource,
 *         TaskScheduler scheduler,
 *         ApplicationEventPublisher publisher) {
 *     return EventProcessorFactory.createProcessor(ProcessorSpec.<MyConfig, String>builder()
 *         .configs(configs)
 *         .processorName("my-processor")
 *         .lockKey(MY_LOCK_KEY)
 *         .instanceId(instanceIdProvider.getInstanceId())
 *         .progressTracker(progressTracker)
 *         .eventFetcher(fetcher)
 *         .eventHandler(handler)
 *         .writeDataSource(writeDataSource)
 *         .taskScheduler(scheduler)
 *         .eventPublisher(publisher)
 *         .build());
 * }
 * }</pre>
 */
public final class EventProcessorFactory {

    private EventProcessorFactory() {}

    /** Creates a processor from named, validated inputs. */
    public static <C extends ProcessorConfig<I>, I> EventProcessor<C, I> createProcessor(
            ProcessorSpec<C, I> spec) {
        LeaderElector elector = spec.leaderElector;
        if (elector == null) {
            elector = createLeaderElector(
                    requireNonNull(spec.writeDataSource), requireNonNull(spec.processorName),
                    requireNonNull(spec.instanceId), requireNonNull(spec.lockKey),
                    spec.eventPublisher);
        }
        return new EventProcessorImpl<>(
                spec.configs, elector, spec.progressTracker, spec.eventFetcher, spec.eventHandler,
                spec.taskScheduler, spec.eventPublisher, spec.wakeupSourceFactory.create(),
                spec.eventPollerConfig.getLeaderRetryCooldownMs(),
                spec.eventPollerConfig.getStartupDelayMs(), spec.clockProvider,
                EventSelection.unionEventTypes(spec.selections),
                EventSelection.unionRequiredTags(spec.selections),
                EventSelection.unionAnyOfTags(spec.selections),
                EventSelection.unionExactTagKeys(spec.selections));
    }

    /**
     * Creates a {@link LeaderElector} for use with
     * {@link #createProcessor(ProcessorSpec)}.
     * Expose this as a separate bean when tests or management components need to inject the elector directly.
     */
    public static LeaderElector createLeaderElector(
            WriteDataSource writeDataSource,
            String processorName,
            String instanceId,
            long lockKey,
            ApplicationEventPublisher eventPublisher) {
        return new LeaderElectorImpl(writeDataSource.dataSource(), processorName, instanceId, lockKey, eventPublisher);
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
            ReadDataSource readDataSource) {
        return new ProcessorManagementServiceImpl<>(eventProcessor, progressTracker, readDataSource.dataSource());
    }
}
