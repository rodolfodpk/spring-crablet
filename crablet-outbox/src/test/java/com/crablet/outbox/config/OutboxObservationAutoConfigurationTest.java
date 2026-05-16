package com.crablet.outbox.config;

import com.crablet.outbox.observability.OutboxObservationListener;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
@DisplayName("OutboxObservationAutoConfiguration")
class OutboxObservationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxObservationAutoConfiguration.class));

    @Test
    @DisplayName("listener is registered when ObservationRegistry bean is present")
    void registersListenerWhenObservationRegistryPresent() {
        runner
                .withUserConfiguration(WithObservationRegistry.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(OutboxObservationListener.class));
    }

    @Test
    @DisplayName("listener is absent when no ObservationRegistry bean is present")
    void noListenerWhenNoObservationRegistry() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxObservationListener.class));
    }

    @Test
    @DisplayName("user-provided listener bean takes precedence")
    void userBeanTakesPrecedence() {
        runner
                .withUserConfiguration(WithObservationRegistry.class, WithCustomListener.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OutboxObservationListener.class);
                    assertThat(ctx.getBean(OutboxObservationListener.class))
                            .isSameAs(ctx.getBean("customListener"));
                });
    }

    @Configuration
    static class WithObservationRegistry {
        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }

    @Configuration
    static class WithCustomListener {
        @Bean("customListener")
        OutboxObservationListener customListener(ObservationRegistry registry) {
            return new OutboxObservationListener(registry);
        }
    }
}
