package com.crablet.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that MicrometerMetricsAutoConfiguration wires correctly under all conditions.
 * No database or component scanning of com.crablet.metrics.micrometer is required —
 * that is the regression this test guards against.
 */
@SuppressWarnings("NullAway")
@DisplayName("MicrometerMetricsAutoConfiguration")
class MicrometerMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MicrometerMetricsAutoConfiguration.class));

    @Test
    @DisplayName("collector is registered when MeterRegistry bean is present")
    void registersCollectorWhenMeterRegistryPresent() {
        runner
            .withUserConfiguration(WithMeterRegistry.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(MicrometerMetricsCollector.class));
    }

    @Test
    @DisplayName("collector is absent when no MeterRegistry bean is present")
    void noCollectorWhenNoMeterRegistry() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(MicrometerMetricsCollector.class));
    }

    @Test
    @DisplayName("user-provided collector bean takes precedence")
    void userBeanTakesPrecedence() {
        runner
            .withUserConfiguration(WithMeterRegistry.class, WithCustomCollector.class)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(MicrometerMetricsCollector.class);
                assertThat(ctx.getBean(MicrometerMetricsCollector.class))
                    .isSameAs(ctx.getBean("customCollector"));
            });
    }

    @Test
    @DisplayName("collector is suppressed when crablet.metrics.enabled=false")
    void suppressedByProperty() {
        runner
            .withUserConfiguration(WithMeterRegistry.class)
            .withPropertyValues("crablet.metrics.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(MicrometerMetricsCollector.class));
    }

    @Configuration
    static class WithMeterRegistry {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration
    static class WithCustomCollector {
        @Bean("customCollector")
        MicrometerMetricsCollector customCollector(MeterRegistry registry) {
            return new MicrometerMetricsCollector(registry);
        }
    }
}
