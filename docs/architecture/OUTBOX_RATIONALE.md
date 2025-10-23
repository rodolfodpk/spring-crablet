# Outbox Pattern Rationale

> **Note**: The outbox pattern is an **optional API** in Crablet. It's designed for applications that need reliable event publishing to external systems. If you don't need to publish events to external systems, you can use Crablet's core event sourcing capabilities without the outbox pattern.

## Why Outbox Pattern?

### The Problem: Reliable Event Publishing

In event-driven architectures, we often need to publish events to external systems (message brokers, webhooks, analytics services) when business events occur. However, this creates a fundamental challenge:

**The Dual Write Problem**: We must update our database AND publish to external systems atomically, but these are separate systems with different failure modes.

```java
// ❌ Problematic approach - not atomic
@Transactional
public void processOrder(OrderCommand command) {
    // Update database
    orderRepository.save(order);
    
    // Publish event - what if this fails?
    eventPublisher.publish(new OrderProcessedEvent(order));
}
```

**What can go wrong:**
- Database succeeds, event publishing fails → Lost event
- Event publishing succeeds, database fails → Inconsistent state
- Network timeouts, partial failures, system crashes
- External systems are temporarily unavailable

### Why Not Just Use a Message Broker?

**Message brokers don't solve the transactional consistency problem:**

1. **No Cross-System Transactions**: Message brokers can't participate in database transactions
2. **Two-Phase Commit Complexity**: Distributed transactions are complex, slow, and unreliable
3. **Single Source of Truth**: Database should remain the authoritative source
4. **External System Failures**: Message brokers can fail independently

## Design Goals

Our outbox implementation aims to provide:

1. **Transactional Consistency**: Events are published atomically with database changes
2. **Reliability**: At-least-once delivery guarantees
3. **Ordering**: Global ordering of events across all publishers
4. **Flexibility**: Pluggable publishers for different external systems
5. **Observability**: Clear visibility into publishing progress and failures
6. **Performance**: Efficient polling and batch processing
7. **Operational Control**: Ability to pause, resume, and reset publishers

## Architecture Decisions

### 1. Topic-Based Routing vs Single Queue

**Decision**: Topic-based routing with tag filtering

**Rationale**:
- **Flexibility**: Different publishers can subscribe to different event types
- **Scalability**: Publishers only process relevant events
- **Maintainability**: Clear separation of concerns
- **Performance**: Reduced processing overhead per publisher

```properties
# Example configuration
crablet.outbox.topics.wallet-events.required-tags=walletId
crablet.outbox.topics.wallet-events.publishers=KafkaPublisher,WebhookPublisher

crablet.outbox.topics.payment-events.any-of-tags=paymentId,transferId
crablet.outbox.topics.payment-events.publishers=AnalyticsPublisher
```

**Alternative Considered**: Single queue with message routing
- **Rejected**: Less flexible, harder to scale, more complex routing logic

### 2. Publisher Registration and Discovery

**Decision**: Spring component scanning with `@Component` annotation

**Rationale**:
- **Simplicity**: Leverages Spring's existing component discovery
- **Type Safety**: Compile-time publisher registration
- **Testability**: Easy to mock and test publishers
- **Configuration**: Publishers configured via properties

```java
@Component
public class KafkaPublisher implements OutboxPublisher {
    @Override
    public String getName() {
        return "KafkaPublisher"; // Used for configuration lookup
    }
}
```

**Alternative Considered**: Runtime registration via API
- **Rejected**: More complex, harder to validate, potential runtime errors

### 3. Leader Election Strategy

**Decision**: `PER_TOPIC_PUBLISHER` with PostgreSQL advisory locks

**Rationale**:
- **Scalability**: Multiple processors can run simultaneously
- **Fault Tolerance**: Processor failures don't affect other publishers
- **Performance**: Parallel processing of different topics
- **Simplicity**: Uses PostgreSQL's built-in advisory lock mechanism

```sql
-- Advisory lock per topic-publisher combination
SELECT pg_advisory_lock(hashtext('topic:default:publisher:LogPublisher'));
```

**Alternative Considered**: `GLOBAL` leader election
- **Rejected**: Single point of failure, limits scalability

### 4. Global Statistics Publisher Always-On Design

**Decision**: Global statistics publisher is always enabled by default

**Rationale**:
- **Operational Visibility**: Essential for monitoring and debugging
- **Minimal Overhead**: Simple logging operation with negligible performance impact
- **No External Dependencies**: Doesn't require external systems to be available
- **Always Available**: Works even when other publishers are disabled

```properties
# Always enabled - no configuration needed
crablet.outbox.global-statistics.enabled=true
crablet.outbox.global-statistics.log-interval-seconds=30
```

**Why Not Optional?**
- **Default Behavior**: Should work out-of-the-box for monitoring
- **Minimal Cost**: Logging is extremely lightweight
- **Operational Value**: Critical for production systems

## Trade-offs Made

### 1. At-Least-Once vs Exactly-Once Delivery

**Decision**: At-least-once delivery

**Trade-offs**:
- ✅ **Gained**: Simplicity, reliability, performance
- ❌ **Sacrificed**: Potential duplicate events

**Rationale**: 
- Duplicate events are easier to handle than lost events
- Idempotent consumers can handle duplicates
- Exactly-once delivery is complex and often impossible

### 2. Polling Interval vs Latency

**Decision**: 1-second polling interval

**Trade-offs**:
- ✅ **Gained**: Reasonable latency, efficient resource usage
- ❌ **Sacrificed**: Sub-second event publishing latency

**Rationale**:
- Most business use cases don't require sub-second latency
- Polling every 100ms would increase database load significantly
- 1-second is a good balance for most applications

### 3. Memory vs Throughput (Batch Size)

**Decision**: Configurable batch size (default: 100 events)

**Trade-offs**:
- ✅ **Gained**: Configurable throughput, memory efficiency
- ❌ **Sacrificed**: Fixed memory usage patterns

**Rationale**:
- Different applications have different throughput requirements
- Batch processing is more efficient than single-event processing
- Memory usage scales with batch size

### 4. Complexity vs Flexibility

**Decision**: Pluggable publisher architecture

**Trade-offs**:
- ✅ **Gained**: Flexibility, extensibility, testability
- ❌ **Sacrificed**: Initial implementation complexity

**Rationale**:
- Long-term maintainability is more important than initial simplicity
- Different teams can implement different publishers
- Easier to test and mock individual publishers

## Alternatives Considered

### 1. Change Data Capture (CDC)

**What it is**: Database-level event capture (e.g., Debezium, AWS DMS)

**Why we didn't choose it**:
- **Complexity**: Requires additional infrastructure
- **Coupling**: Tightly coupled to database internals
- **Filtering**: Harder to implement business logic filtering
- **Ordering**: May not preserve application-level ordering

### 2. Event Sourcing with Projections

**What it is**: Rebuild state from events, publish projections

**Why we didn't choose it**:
- **Performance**: Rebuilding state is expensive
- **Complexity**: Requires projection management
- **Storage**: Duplicate storage of events and projections

### 3. Saga Pattern

**What it is**: Distributed transaction management across services

**Why we didn't choose it**:
- **Complexity**: Complex compensation logic
- **Reliability**: Harder to ensure consistency
- **Performance**: Synchronous coordination overhead

### 4. Database Triggers

**What it is**: Database-level triggers to publish events

**Why we didn't choose it**:
- **Portability**: Database-specific implementation
- **Testing**: Harder to test and debug
- **Flexibility**: Limited business logic in triggers

## When to Use

### ✅ Good Use Cases

The outbox pattern is **optional** and should only be used when you need to publish events to external systems:

1. **Event-Driven Microservices**: Publishing domain events to other services
2. **Analytics and Reporting**: Sending events to data warehouses
3. **Audit and Compliance**: Ensuring all events are captured
4. **Integration**: Connecting with external systems and APIs
5. **CQRS**: Publishing events for read model updates

### Example Scenarios

```java
// ✅ Good: Domain event publishing
@Transactional
public void processPayment(PaymentCommand command) {
    // Business logic
    Payment payment = paymentService.process(command);
    
    // Event stored atomically with payment
    eventStore.append(payment.getEvents());
    // Outbox will publish PaymentProcessedEvent to Kafka, webhooks, etc.
}

// ✅ Good: Analytics events
@Transactional
public void userLogin(UserLoginCommand command) {
    // Business logic
    userService.recordLogin(command);
    
    // Analytics event stored atomically
    eventStore.append(new UserLoginEvent(command.getUserId()));
    // Outbox will publish to analytics service
}
```

## When NOT to Use

### ❌ Anti-Patterns and Limitations

**Don't use the outbox pattern if you don't need external event publishing:**

1. **High-Frequency, Low-Latency Events**: Use direct messaging instead
2. **Exactly-Once Requirements**: Pattern provides at-least-once only
3. **Simple CRUD Applications**: Overkill for basic operations
4. **Real-Time Systems**: Polling adds latency
5. **External System as Source of Truth**: Database should remain authoritative
6. **Internal-Only Event Sourcing**: If you only need event sourcing without external publishing

### Example Anti-Patterns

```java
// ❌ Anti-pattern: High-frequency events
@Transactional
public void updateUserLocation(UserLocationCommand command) {
    // This happens 1000x per second - outbox polling is too slow
    userService.updateLocation(command);
    eventStore.append(new LocationUpdatedEvent(command));
}

// ❌ Anti-pattern: External system as source of truth
@Transactional
public void syncWithExternalSystem(SyncCommand command) {
    // Don't use outbox if external system is the source of truth
    externalSystem.update(command);
    eventStore.append(new SyncEvent(command));
}

// ❌ Anti-pattern: Internal-only event sourcing
@Transactional
public void processOrder(OrderCommand command) {
    // If you only need event sourcing for audit/replay, don't use outbox
    orderService.process(command);
    eventStore.append(command.getEvents());
    // No external publishing needed - outbox is unnecessary overhead
}
```

## Core Crablet vs Outbox Pattern

### Core Crablet (Always Available)

Crablet's core functionality provides:

- **Event Sourcing**: Store and replay events
- **DCB Pattern**: Concurrency control and consistency
- **State Projections**: Reconstruct state from events
- **Query System**: Filter and paginate events
- **Command Processing**: Handle business commands

```java
// Core Crablet usage - no outbox needed
@Transactional
public void processOrder(OrderCommand command) {
    // Business logic
    Order order = orderService.process(command);
    
    // Store events for audit/replay/state reconstruction
    eventStore.append(order.getEvents());
    // That's it - no external publishing needed
}
```

### Outbox Pattern (Optional Add-on)

The outbox pattern adds:

- **External Publishing**: Send events to message brokers, webhooks, etc.
- **Reliability**: At-least-once delivery guarantees
- **Ordering**: Global ordering across publishers
- **Operational Control**: Pause, resume, reset publishers

```java
// With outbox pattern - for external publishing
@Transactional
public void processOrder(OrderCommand command) {
    // Business logic
    Order order = orderService.process(command);
    
    // Store events (same as core Crablet)
    eventStore.append(order.getEvents());
    
    // Outbox will automatically publish to external systems
    // (Kafka, webhooks, analytics, etc.)
}
```

## Performance Considerations

### 1. Why READ COMMITTED is Sufficient

**Decision**: Use READ COMMITTED isolation level

**Rationale**:
- **Advisory Locks**: `append_events_if` uses `pg_advisory_xact_lock()` for serialization
- **Gap-Free Positions**: Serialized inserts ensure position values increment without gaps
- **Eventual Consistency**: 1-second polling makes eventual consistency acceptable
- **Performance**: READ COMMITTED is faster than higher isolation levels

### 2. Position-Based Polling Efficiency

**Why it's efficient**:
- **Index Usage**: `WHERE position > last_position` uses primary key index
- **Incremental**: Only fetches new events since last position
- **Batch Processing**: Processes multiple events in single query

### 3. Batch Processing Benefits

**Performance advantages**:
- **Reduced Network Overhead**: Fewer round trips to external systems
- **Better Throughput**: Publishers can optimize for batch operations
- **Connection Pooling**: More efficient use of database connections

### 4. Advisory Lock Overhead

**Minimal impact**:
- **Per-Transaction**: Locks are held only during transaction
- **PostgreSQL Optimized**: Advisory locks are highly optimized in PostgreSQL
- **Short Duration**: Locks held only during event append operations

## Implementation Benefits

### 1. Reliability
- **No Lost Events**: Events are stored atomically with business data
- **Retry Logic**: Failed publishers are automatically retried
- **Monitoring**: Clear visibility into publisher status and failures

### 2. Maintainability
- **Separation of Concerns**: Business logic separate from publishing logic
- **Testability**: Easy to test business logic without external dependencies
- **Configuration**: Publishers configured via properties, not code

### 3. Operational Excellence
- **Pause/Resume**: Can pause publishers during maintenance
- **Reset Failed Publishers**: Can reset failed publishers without data loss
- **Lag Monitoring**: Clear visibility into publisher lag and performance

### 4. Scalability
- **Horizontal Scaling**: Multiple processors can run simultaneously
- **Publisher Isolation**: Publisher failures don't affect other publishers
- **Configurable Batching**: Can tune batch sizes for different throughput requirements

## Conclusion

The outbox pattern provides a robust, scalable solution for reliable event publishing in event-driven architectures. While it adds some complexity, the benefits of transactional consistency, reliability, and operational control make it a valuable pattern for systems that need to publish events to external systems reliably.

The key is understanding when to use it (event-driven architectures with external integrations) and when not to use it (simple CRUD applications or high-frequency, low-latency requirements). When used appropriately, it significantly improves system reliability and maintainability.
