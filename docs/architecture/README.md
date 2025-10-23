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

## Package Structure

### Crablet Library

- **`com.crablet.core`**: Pure interfaces (EventStore, CommandExecutor, domain models) - no Spring dependencies
- **`com.crablet.impl`**: Spring implementations (JDBCEventStore, DefaultCommandExecutor, EventStoreConfig)

Architecture tests enforce this separation to keep core contracts framework-agnostic.

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

Cursor-based optimistic locking:

- **Entity Scoping**: Conflicts checked per entity (e.g., per wallet)
- **Position Checks**: "Any events for entity X after position N?"
- **PostgreSQL Integration**: `pg_snapshot_xmin()` for committed-only checks
- **Conflict Response**: 409 status code triggers client retry

Measured throughput: 700+ req/s with entity-scoped checks.

See **[DCB Technical Details](DCB_AND_CRABLET.md)** for implementation walkthrough.

### Append Conditions

Declarative conflict detection:
- Filter by event types
- Filter by entity tags
- Check after cursor position
- Return 409 on conflict

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

## Outbox Pattern

Reliable event publishing to external systems using the transactional outbox pattern:

- **[Outbox Pattern Implementation](OUTBOX_PATTERN.md)** - Technical implementation details
- **[Outbox Pattern Rationale](OUTBOX_RATIONALE.md)** - Design decisions and trade-offs

## Related Documentation

- [API Reference](../api/README.md) - API documentation
- [Development Guide](../development/README.md) - Development practices
- [Setup Guide](../setup/README.md) - Installation
- [Observability](../observability/README.md) - Monitoring
