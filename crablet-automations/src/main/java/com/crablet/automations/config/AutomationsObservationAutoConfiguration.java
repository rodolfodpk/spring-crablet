package com.crablet.automations.config;

import com.crablet.automations.observability.AutomationObservationListener;
import com.crablet.eventstore.Internal;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Internal
@AutoConfiguration(after = AutomationsAutoConfiguration.class)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class AutomationsObservationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AutomationObservationListener automationObservationListener(ObservationRegistry observationRegistry) {
        return new AutomationObservationListener(observationRegistry);
    }
}
