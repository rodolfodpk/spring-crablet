package com.crablet.eventstore.config;

import com.crablet.eventstore.Internal;
import com.crablet.eventstore.observability.EventStoreObservationListener;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Internal
@AutoConfiguration(after = EventStoreAutoConfiguration.class)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class EventStoreObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventStoreObservationListener eventStoreObservationListener(
            ObservationRegistry observationRegistry) {
        return new EventStoreObservationListener(observationRegistry);
    }
}
