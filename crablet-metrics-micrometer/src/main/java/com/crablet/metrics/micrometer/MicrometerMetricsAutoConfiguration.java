package com.crablet.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Crablet Micrometer metrics.
 * <p>
 * Activates when:
 * <ul>
 *   <li>{@link MeterRegistry} is on the classpath (i.e. micrometer-core is a dependency)</li>
 *   <li>A {@link MeterRegistry} bean is present in the application context</li>
 *   <li>{@code crablet.metrics.enabled} is {@code true} or not set (default: enabled)</li>
 * </ul>
 * <p>
 * No component scanning of {@code com.crablet.metrics.micrometer} is required.
 * Simply adding this module's dependency is enough for auto-configuration to kick in.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class MicrometerMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "crablet.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MicrometerMetricsCollector micrometerMetricsCollector(MeterRegistry registry) {
        return new MicrometerMetricsCollector(registry);
    }
}
