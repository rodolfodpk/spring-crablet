package com.crablet.eventpoller.config;

import com.crablet.eventpoller.InstanceIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * </ul>
 * <p>
 * Both beans use {@code @ConditionalOnMissingBean}, so you can override either one
 * by declaring your own bean of the same type (or name, for the scheduler) in your
 * application context.
 */
@AutoConfiguration
public class EventPollerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InstanceIdProvider instanceIdProvider(Environment environment) {
        return new InstanceIdProvider(environment);
    }

    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("crablet-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}
