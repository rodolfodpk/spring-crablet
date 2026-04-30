package com.crablet.course.config;

import com.crablet.command.web.CommandApiExposedCommands;
import com.crablet.outbox.config.OutboxConfig;
import com.crablet.outbox.config.TopicConfigurationProperties;
import com.crablet.outbox.publishers.LogPublisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-specific bean configuration.
 * <p>
 * Framework infrastructure (EventStore, CommandExecutor, DataSources, TaskScheduler,
 * InstanceIdProvider, ClockProvider, ObjectMapper) is auto-configured by Spring Boot
 * and the crablet modules. Only beans that are course-domain-specific or
 * that override framework defaults belong here.
 */
@Configuration
@EnableConfigurationProperties({OutboxConfig.class, TopicConfigurationProperties.class})
public class CrabletConfig {

    @Bean
    @ConditionalOnMissingBean(name = "logPublisher")
    public LogPublisher logPublisher() {
        return new LogPublisher();
    }

    /**
     * Expose all course commands via the generic HTTP command API (POST /api/commands).
     */
    @Bean
    public CommandApiExposedCommands commandApiExposedCommands() {
        return CommandApiExposedCommands.fromPackages("com.crablet.examples.course");
    }
}
