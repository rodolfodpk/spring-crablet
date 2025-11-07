# Required Dependencies Pattern

This document explains how Crablet handles dependencies, specifically for metrics collection.

## Overview

Crablet uses constructor injection for all dependencies. Metrics dependencies (`ApplicationEventPublisher` and `GlobalStatisticsPublisher`) are required parameters. Spring Boot automatically provides `ApplicationEventPublisher`, making metrics enabled by default.

## Pattern

### Constructor Injection with Required Parameters

All dependencies are passed as required constructor parameters:

```java
public class CommandExecutorImpl implements CommandExecutor {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public CommandExecutorImpl(
            EventStore eventStore,
            List<CommandHandler<?>> commandHandlers,
            EventStoreConfig config,
            ClockProvider clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        // ... required dependencies ...
        this.eventPublisher = eventPublisher;
    }
}
```

### Usage

The dependency is used directly without null checks:

```java
eventPublisher.publishEvent(new CommandStartedMetric(commandType, startTime));
```

## Rationale

### Why Constructor Injection?

1. **Immutability**: Fields can be `final`, making objects immutable after construction
2. **Required Dependencies**: Forces all dependencies to be provided at construction time
3. **Thread Safety**: No risk of partially constructed objects
4. **Testability**: Easy to test - just pass dependencies in constructor
5. **Clear Dependencies**: Constructor signature shows exactly what's needed
6. **No Hidden Dependencies**: All dependencies are explicit in the constructor

### Why Not Setter Injection?

Setter injection has several problems:
- Fields cannot be `final` (object can be modified after construction)
- Easy to forget to call setter, leading to NPEs
- Object can exist in invalid state
- Thread safety issues if setters are called after construction
- Hidden dependencies not visible in constructor signature

### Why Not Field Injection?

Field injection with `@Autowired(required = false)` has issues:
- Fields cannot be `final` (not immutable)
- Hidden dependencies (not visible in constructor)
- Harder to test (requires Spring context or reflection)
- Spring-specific (tight coupling to Spring)

## Best Practices

### Constructor Injection Benefits

Constructor injection provides:
- **Immutability**: Fields can be `final`, making objects immutable after construction
- **Required Dependencies**: Forces all dependencies to be provided at construction time
- **Thread Safety**: No risk of partially constructed objects
- **Testability**: Easy to test - just pass dependencies in constructor
- **Clear Dependencies**: Constructor signature shows exactly what's needed
- **No Hidden Dependencies**: All dependencies are explicit in the constructor

## Examples in Crablet

### CommandExecutorImpl

```java
public CommandExecutorImpl(
        EventStore eventStore,
        List<CommandHandler<?>> commandHandlers,
        EventStoreConfig config,
        ClockProvider clock,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher) {
    // eventPublisher is required - Spring Boot provides this automatically
}
```

### EventStoreImpl

```java
public EventStoreImpl(
        DataSource writeDataSource,
        DataSource readDataSource,
        ObjectMapper objectMapper,
        EventStoreConfig config,
        ClockProvider clock,
        ApplicationEventPublisher eventPublisher) {
    // eventPublisher is required - Spring Boot provides this automatically
}
```

### Outbox Components

All outbox components (`OutboxPublishingServiceImpl`, `OutboxProcessorImpl`, `OutboxLeaderElector`) require `ApplicationEventPublisher` and `GlobalStatisticsPublisher` as constructor parameters.

## Configuration

### Enabling Metrics

Metrics are enabled by default when using Spring Boot, which automatically provides an `ApplicationEventPublisher` bean:

```java
@Bean
public CommandExecutor commandExecutor(
        EventStore eventStore,
        List<CommandHandler<?>> commandHandlers,
        EventStoreConfig config,
        ClockProvider clock,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher) {  // Spring Boot provides this automatically
    return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
}
```

### Disabling Metrics

If you need to disable metrics (not recommended), you can provide a no-op implementation:

```java
@Bean
public ApplicationEventPublisher noOpEventPublisher() {
    return event -> {};  // No-op implementation
}

@Bean
public CommandExecutor commandExecutor(
        EventStore eventStore,
        List<CommandHandler<?>> commandHandlers,
        EventStoreConfig config,
        ClockProvider clock,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher) {
    return new CommandExecutorImpl(eventStore, commandHandlers, config, clock, objectMapper, eventPublisher);
}
```

## Spring Integration

Since Crablet is tied to Spring (uses Spring types like `ApplicationEventPublisher`, `JdbcTemplate`, etc.), Spring Boot automatically provides `ApplicationEventPublisher` as a bean, making metrics enabled by default.

## Testing

When testing, provide a mock for `ApplicationEventPublisher`:

```java
ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
CommandExecutorImpl executor = new CommandExecutorImpl(
    eventStore,
    List.of(commandHandler),
    config,
    clock,
    objectMapper,
    mockPublisher
);
```

## Summary

- **Pattern**: Constructor injection with required parameters
- **Rationale**: Immutability, thread safety, testability, clear dependencies
- **Metrics**: Enabled by default when using Spring Boot
- **Testing**: Provide mocks for `ApplicationEventPublisher` and `GlobalStatisticsPublisher`

