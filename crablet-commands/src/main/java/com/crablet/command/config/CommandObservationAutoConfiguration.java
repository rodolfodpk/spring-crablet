package com.crablet.command.config;

import com.crablet.command.observability.CommandObservationListener;
import com.crablet.eventstore.Internal;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Internal
@AutoConfiguration(after = CommandAutoConfiguration.class)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class CommandObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommandObservationListener commandObservationListener(ObservationRegistry observationRegistry) {
        return new CommandObservationListener(observationRegistry);
    }
}
