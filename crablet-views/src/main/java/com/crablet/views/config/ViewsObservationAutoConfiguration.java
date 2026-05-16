package com.crablet.views.config;

import com.crablet.eventstore.Internal;
import com.crablet.views.observability.ViewObservationListener;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Internal
@AutoConfiguration(after = ViewsAutoConfiguration.class)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class ViewsObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ViewObservationListener viewObservationListener(ObservationRegistry observationRegistry) {
        return new ViewObservationListener(observationRegistry);
    }
}
