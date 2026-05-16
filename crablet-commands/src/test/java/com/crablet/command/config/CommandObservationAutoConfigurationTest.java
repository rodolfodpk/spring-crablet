package com.crablet.command.config;

import com.crablet.command.observability.CommandObservationListener;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
@DisplayName("CommandObservationAutoConfiguration")
class CommandObservationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommandObservationAutoConfiguration.class));

    @Test
    @DisplayName("listener is registered when ObservationRegistry bean is present")
    void registersListenerWhenObservationRegistryPresent() {
        runner
                .withUserConfiguration(WithObservationRegistry.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(CommandObservationListener.class));
    }

    @Test
    @DisplayName("listener is absent when no ObservationRegistry bean is present")
    void noListenerWhenNoObservationRegistry() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CommandObservationListener.class));
    }

    @Test
    @DisplayName("user-provided listener bean takes precedence")
    void userBeanTakesPrecedence() {
        runner
                .withUserConfiguration(WithObservationRegistry.class, WithCustomListener.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(CommandObservationListener.class);
                    assertThat(ctx.getBean(CommandObservationListener.class))
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
        CommandObservationListener customListener(ObservationRegistry registry) {
            return new CommandObservationListener(registry);
        }
    }
}
