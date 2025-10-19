# Architecture Documentation

Event sourcing implementation using the Dynamic Consistency Boundary (DCB) pattern, built with Spring Boot and
PostgreSQL.

## System Overview

The wallet challenge solution implements event sourcing with:

- **EventStore**: Central component for event operations (append, query, projection)
- **DCB Pattern**: Dynamic Consistency Boundary for concurrency control
- **PostgreSQL**: Event storage with ACID compliance
- **Spring Boot**: Application framework with REST API

## Core Components

### EventStore Interface

- **Append**: Store new events with concurrency control
- **Query**: Retrieve events with filtering and pagination
- **Projection**: Reconstruct state from events

### Event Model

Immutable event records with:

- **Tags**: Key-value pairs for categorization
- **Data**: Serialized event payload (JSON)
- **Metadata**: Timestamp, version, and trace information

### Query System

- **Tag Filters**: Filter by specific tag values
- **Type Filters**: Filter by event types
- **Pagination**: Efficient large dataset handling with cursors

## Database Integration

### PostgreSQL Backend

- **JDBC Driver**: Traditional database connectivity
- **Connection Pooling**: HikariCP for performance
- **Schema**: Events table, commands table, PostgreSQL functions
- **Indexes**: Optimized for query performance

## Wallet Domain Implementation

### Domain Model

#### Commands

- `OpenWalletCommand`: Create new wallet
- `DepositCommand`: Add money to wallet
- `WithdrawCommand`: Remove money from wallet
- `TransferMoneyCommand`: Transfer between wallets

#### Events

- `WalletOpened`: Wallet creation event
- `DepositMade`: Money added event
- `WithdrawalMade`: Money removed event
- `MoneyTransferred`: Transfer completion event

#### State Projection

- `WalletState`: Current wallet state
- **Balance Calculation**: Sum of all transactions
- **Event Replay**: State reconstruction from events

### Command Handler

- **Validation**: Command validation rules
- **Concurrency Control**: Optimistic locking
- **Event Generation**: Command to event transformation
- **Error Handling**: Comprehensive error management

## REST API Design

### API Features

- **Idempotency**: Safe operation repetition
- **Validation**: Input validation and error handling
- **Pagination**: Efficient large dataset handling
- **Swagger Documentation**: Interactive API documentation

See [API Reference](api/README.md) for complete API documentation.

## Concurrency Control

### Dynamic Consistency Boundary (DCB)

Event sourcing concurrency pattern:

- **Optimistic Locking**: Conflict detection and resolution
- **Event Ordering**: Consistent event ordering
- **Conflict Resolution**: Automatic retry mechanisms
- **Consistency Guarantees**: Eventual consistency

### Append Conditions

- **Version Checking**: Event version validation
- **Conditional Appends**: Atomic event appends
- **Conflict Detection**: Automatic conflict identification
- **Retry Logic**: Automatic retry on conflicts

## Serialization

### Event Serialization

Jackson-based serialization:

- **Sealed Interfaces**: Type-safe event hierarchies
- **JSON Format**: Human-readable event format
- **Version Compatibility**: Backward compatibility support

## Testing Strategy

### Test Architecture

- **Unit Tests**: Fast, isolated component testing
- **Integration Tests**: Full-stack testing with TestContainers
- **Performance Tests**: Load testing with k6

### Test Categories

- **Fast Tests**: Unit tests (< 1s)
- **Slow Tests**: Integration tests (> 1s)
- **Performance Tests**: Load and stress testing

## Related Documentation

- [API Reference](../api/README.md) - API documentation
- [Development Guide](../development/README.md) - Development practices
- [Setup Guide](../setup/README.md) - Installation
- [Observability](../observability/README.md) - Monitoring
