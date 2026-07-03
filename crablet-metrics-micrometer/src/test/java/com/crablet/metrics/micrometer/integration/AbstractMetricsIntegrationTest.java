package com.crablet.metrics.micrometer.integration;

import com.crablet.test.AbstractPostgresEventStoreTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Base class for metrics integration tests.
 * Sets up EventStore with MicrometerMetricsCollector and verifies metrics are collected.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
public abstract class AbstractMetricsIntegrationTest extends AbstractPostgresEventStoreTest {

    @Autowired
    protected MeterRegistry meterRegistry;

    @Autowired
    protected ApplicationContext applicationContext;
}
