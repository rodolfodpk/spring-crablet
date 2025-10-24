package com.wallets.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for custom business metrics.
 * Provides beans for wallet operations, financial metrics, and event store metrics.
 */
@Configuration
public class MetricsConfig {

    // This configuration class is intentionally minimal.
    // Custom metrics are created dynamically in WalletService using Counter.builder() and Timer.builder()
    // to avoid complex bean dependency issues with Micrometer's API.

    public MetricsConfig(MeterRegistry meterRegistry) {
        // Constructor injection to ensure MeterRegistry is available
        // Individual metrics are created as needed in service classes
    }
}
