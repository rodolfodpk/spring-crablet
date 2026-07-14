package com.crablet.eventpoller;

import com.crablet.eventpoller.config.EventPollerConfig;
import com.crablet.eventpoller.leader.LeaderElector;
import com.crablet.eventpoller.processor.ProcessorConfig;
import com.crablet.eventpoller.progress.ProgressTracker;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.WriteDataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Named, validated inputs used to create an event processor. */
public final class ProcessorSpec<C extends ProcessorConfig<I>, I> {

    final Map<I, C> configs;
    final @Nullable LeaderElector leaderElector;
    final @Nullable String processorName;
    final @Nullable Long lockKey;
    final @Nullable String instanceId;
    final @Nullable WriteDataSource writeDataSource;
    final ProgressTracker<I> progressTracker;
    final EventFetcher<I> eventFetcher;
    final EventHandler<I> eventHandler;
    final TaskScheduler taskScheduler;
    final ApplicationEventPublisher eventPublisher;
    final ProcessorWakeupSourceFactory wakeupSourceFactory;
    final EventPollerConfig eventPollerConfig;
    final ClockProvider clockProvider;
    final Collection<? extends EventSelection> selections;

    private ProcessorSpec(Builder<C, I> builder) {
        this.configs = require(builder.configs, "configs");
        this.leaderElector = builder.leaderElector;
        this.processorName = builder.processorName;
        this.lockKey = builder.lockKey;
        this.instanceId = builder.instanceId;
        this.writeDataSource = builder.writeDataSource;
        this.progressTracker = require(builder.progressTracker, "progressTracker");
        this.eventFetcher = require(builder.eventFetcher, "eventFetcher");
        this.eventHandler = require(builder.eventHandler, "eventHandler");
        this.taskScheduler = require(builder.taskScheduler, "taskScheduler");
        this.eventPublisher = require(builder.eventPublisher, "eventPublisher");
        this.wakeupSourceFactory = require(builder.wakeupSourceFactory, "wakeupSourceFactory");
        this.eventPollerConfig = require(builder.eventPollerConfig, "eventPollerConfig");
        this.clockProvider = require(builder.clockProvider, "clockProvider");
        this.selections = List.copyOf(require(builder.selections, "selections"));

        boolean suppliedElector = leaderElector != null;
        boolean suppliedAnyElectionSetting = processorName != null || lockKey != null
                || instanceId != null || writeDataSource != null;
        boolean suppliedAllElectionSettings = processorName != null && lockKey != null
                && instanceId != null && writeDataSource != null;
        if ((suppliedElector && suppliedAnyElectionSetting)
                || (!suppliedElector && !suppliedAllElectionSettings)) {
            throw new IllegalArgumentException(
                    "Specify either leaderElector or all leader-election settings");
        }
    }

    public static <C extends ProcessorConfig<I>, I> Builder<C, I> builder() {
        return new Builder<>();
    }

    private static <T> T require(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /** Builder with no-op wakeup, default poller configuration and no selections. */
    public static final class Builder<C extends ProcessorConfig<I>, I> {
        private @Nullable Map<I, C> configs;
        private @Nullable LeaderElector leaderElector;
        private @Nullable String processorName;
        private @Nullable Long lockKey;
        private @Nullable String instanceId;
        private @Nullable WriteDataSource writeDataSource;
        private @Nullable ProgressTracker<I> progressTracker;
        private @Nullable EventFetcher<I> eventFetcher;
        private @Nullable EventHandler<I> eventHandler;
        private @Nullable TaskScheduler taskScheduler;
        private @Nullable ApplicationEventPublisher eventPublisher;
        private ProcessorWakeupSourceFactory wakeupSourceFactory =
                new NoopProcessorWakeupSourceFactory();
        private EventPollerConfig eventPollerConfig = new EventPollerConfig();
        private ClockProvider clockProvider = ClockProvider.systemDefault();
        private Collection<? extends EventSelection> selections = List.of();

        private Builder() {}

        public Builder<C, I> configs(Map<I, C> value) { configs = value; return this; }
        public Builder<C, I> leaderElector(LeaderElector value) { leaderElector = value; return this; }
        public Builder<C, I> processorName(String value) { processorName = value; return this; }
        public Builder<C, I> lockKey(long value) { lockKey = value; return this; }
        public Builder<C, I> instanceId(String value) { instanceId = value; return this; }
        public Builder<C, I> writeDataSource(WriteDataSource value) { writeDataSource = value; return this; }
        public Builder<C, I> progressTracker(ProgressTracker<I> value) { progressTracker = value; return this; }
        public Builder<C, I> eventFetcher(EventFetcher<I> value) { eventFetcher = value; return this; }
        public Builder<C, I> eventHandler(EventHandler<I> value) { eventHandler = value; return this; }
        public Builder<C, I> taskScheduler(TaskScheduler value) { taskScheduler = value; return this; }
        public Builder<C, I> eventPublisher(ApplicationEventPublisher value) { eventPublisher = value; return this; }
        public Builder<C, I> wakeupSourceFactory(ProcessorWakeupSourceFactory value) { wakeupSourceFactory = value; return this; }
        public Builder<C, I> eventPollerConfig(EventPollerConfig value) { eventPollerConfig = value; return this; }
        public Builder<C, I> clockProvider(ClockProvider value) { clockProvider = value; return this; }
        public Builder<C, I> selections(Collection<? extends EventSelection> value) { selections = value; return this; }

        public ProcessorSpec<C, I> build() { return new ProcessorSpec<>(this); }
    }
}
