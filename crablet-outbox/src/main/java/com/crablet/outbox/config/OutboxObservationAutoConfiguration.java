package com.crablet.outbox.config;

import com.crablet.eventstore.Internal;
import com.crablet.outbox.observability.OutboxObservationListener;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Internal
@AutoConfiguration(after = OutboxAutoConfiguration.class)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class OutboxObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxObservationListener outboxObservationListener(ObservationRegistry observationRegistry) {
        return new OutboxObservationListener(observationRegistry);
    }
}
