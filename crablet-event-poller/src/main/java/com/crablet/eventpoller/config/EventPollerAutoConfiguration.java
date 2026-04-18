package com.crablet.eventpoller.config;

import com.crablet.eventpoller.InstanceIdProvider;
import com.crablet.eventpoller.wakeup.NoopProcessorWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.PostgresNotifyWakeupSourceFactory;
import com.crablet.eventpoller.wakeup.ProcessorWakeupSourceFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Auto-configuration for Crablet event-poller infrastructure.
 * <p>
 * Provides shared infrastructure needed by poller-backed modules
 * (views, automations, outbox):
 * <ul>
 *   <li>{@link InstanceIdProvider} — resolved from {@code HOSTNAME},
 *       {@code crablet.instance.id}, or the host name</li>
 *   <li>{@link TaskScheduler} — thread-pool scheduler named {@code taskScheduler}</li>
 *   <li>{@link EventPollerConfig} — tunable infrastructure defaults</li>
 * </ul>
 * <p>
 * All beans use {@code @ConditionalOnMissingBean}, so you can override any of them
 * by declaring your own bean of the same type (or name, for the scheduler) in your
 * application context.
 */
@AutoConfiguration
@EnableConfigurationProperties({EventPollerNotificationProperties.class, EventPollerConfig.class})
public class EventPollerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InstanceIdProvider instanceIdProvider(Environment environment) {
        return new InstanceIdProvider(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPollerConfig eventPollerConfig() {
        return new EventPollerConfig();
    }

    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public TaskScheduler taskScheduler(EventPollerConfig eventPollerConfig) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(eventPollerConfig.getScheduler().getPoolSize());
        scheduler.setThreadNamePrefix("crablet-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(eventPollerConfig.getScheduler().getAwaitTerminationSeconds());
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessorWakeupSourceFactory processorWakeupSourceFactory(
            EventPollerNotificationProperties notificationProperties) {
        if (notificationProperties.getJdbcUrl() == null || notificationProperties.getJdbcUrl().isBlank()) {
            return new NoopProcessorWakeupSourceFactory();
        }

        return new PostgresNotifyWakeupSourceFactory(
                notificationProperties.getJdbcUrl(),
                notificationProperties.getUsername(),
                notificationProperties.getPassword(),
                notificationProperties.getChannel());
    }
}
