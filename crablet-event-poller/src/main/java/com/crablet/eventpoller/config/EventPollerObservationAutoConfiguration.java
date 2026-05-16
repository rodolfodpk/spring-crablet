package com.crablet.eventpoller.config;

import com.crablet.eventpoller.observability.EventPollerObservationListener;
import com.crablet.eventstore.Internal;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Internal
@AutoConfiguration(after = EventPollerAutoConfiguration.class)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class EventPollerObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventPollerObservationListener eventPollerObservationListener(ObservationRegistry observationRegistry) {
        return new EventPollerObservationListener(observationRegistry);
    }
}
