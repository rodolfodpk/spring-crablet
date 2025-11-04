# Crablet Metrics - Micrometer

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_metrics)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Micrometer metrics collector for Crablet event-driven metrics.

## Overview

This module provides automatic metrics collection for Crablet using Micrometer. It subscribes to metric events published via Spring Events and records them to Micrometer.

**Framework-agnostic design**: The core Crablet modules (`crablet-eventstore`, `crablet-command`, `crablet-outbox`) publish framework-agnostic metric events. This module collects those events and records them to Micrometer.

## Features

- **Automatic discovery**: Auto-configures when on the classpath
- **Framework-agnostic**: Core modules don't depend on Micrometer
- **Comprehensive metrics**: Collects metrics from EventStore, Command, and Outbox modules
- **Optional**: If this module is not on the classpath, metrics are simply not recorded (no errors)

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-metrics-micrometer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Setup

1. Add the dependency to your `pom.xml` (see Maven Coordinates above)
2. Ensure `MeterRegistry` is available (Spring Boot provides this automatically)
3. The collector auto-configures via Spring component scanning

### Metrics Collected

#### EventStore Metrics

- `eventstore.events.appended` - Total number of events appended
- `eventstore.events.by_type` - Events appended by type (tagged with `event_type`)
- `eventstore.concurrency.violations` - Total number of DCB concurrency violations

#### Command Metrics

- `eventstore.commands.duration` - Command execution time (tagged with `command_type`)
- `eventstore.commands.total` - Total commands processed (tagged with `command_type`)
- `eventstore.commands.failed` - Failed commands (tagged with `command_type` and `error_type`)
- `eventstore.commands.idempotent` - Idempotent operations (tagged with `command_type`)

#### Outbox Metrics

- `outbox.events.published` - Total number of events published (tagged with `publisher`)
- `outbox.processing.cycles` - Total number of processing cycles
- `outbox.errors` - Total number of publishing errors (tagged with `publisher`)
- `outbox.is_leader` - Whether this instance is the outbox leader (1=leader, 0=follower, tagged with `instance`)

## Example

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
    
    // MeterRegistry is provided by Spring Boot Actuator
    // MicrometerMetricsCollector auto-configures
}
```

## How It Works

1. **Event Publishing**: Core modules publish metric events via Spring `ApplicationEventPublisher`
2. **Event Collection**: `MicrometerMetricsCollector` subscribes to these events using `@EventListener`
3. **Metrics Recording**: Events are converted to Micrometer metrics (counters, timers, gauges)

## Customization

The collector is a Spring `@Component`, so you can:

- **Override**: Define your own `MicrometerMetricsCollector` bean to customize behavior
- **Disable**: Exclude the package from component scanning to disable auto-configuration
- **Extend**: Create additional collectors that subscribe to the same metric events

## Framework Alternatives

This module provides Micrometer support. You can implement similar collectors for other frameworks:

- **OpenTelemetry**: Create `crablet-metrics-opentelemetry` module
- **Custom**: Implement your own collector that subscribes to metric events

## Dependencies

- `crablet-eventstore` - For EventStore metric events
- `crablet-command` - For Command metric events
- `crablet-outbox` - For Outbox metric events
- `micrometer-core` - For metrics recording
- `spring-boot-starter` - For Spring Events and component scanning

## Migration from Deprecated Metrics

The old metrics classes (`EventStoreMetrics`, `OutboxMetrics`, `OutboxPublisherMetrics`) are deprecated and will be removed in version 2.0.0.

**Migration steps:**
1. Add `crablet-metrics-micrometer` dependency
2. Remove explicit `EventStoreMetrics` bean definitions (if any)
3. Metrics will be collected automatically via Spring Events

**Note**: The old metrics classes are still functional for backward compatibility, but new code should use the event-driven approach.

## See Also

- [Crablet EventStore README](../crablet-eventstore/README.md)
- [Crablet Command README](../crablet-command/README.md)
- [Crablet Outbox README](../crablet-outbox/README.md)

