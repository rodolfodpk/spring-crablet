# Wallet EventStore Service

Microservice handling wallet business operations with event sourcing.

## Overview

This service provides the business API for wallet operations using the Crablet event sourcing library. It handles all wallet commands and queries, storing events in PostgreSQL.

## API Endpoints

### Wallet Operations
- `POST /api/wallets` - Open a new wallet
- `POST /api/wallets/{id}/deposit` - Deposit money into wallet
- `POST /api/wallets/{id}/withdraw` - Withdraw money from wallet
- `POST /api/wallets/{id}/transfer` - Transfer money between wallets

### Query Operations
- `GET /api/wallets/{id}` - Get wallet state
- `GET /api/wallets/{id}/history` - Get wallet event history
- `GET /api/wallets/{id}/commands` - Get wallet commands

### Health & Metrics
- `GET /actuator/health` - Service health check
- `GET /actuator/metrics` - Service metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Architecture

### Event Sourcing
- Commands are processed by command handlers
- Events are stored in PostgreSQL `events` table
- Commands are stored in PostgreSQL `commands` table
- State is projected from events using projectors

### Database Schema
- `events` - Event store table
- `commands` - Command store table
- `outbox_topic_progress` - Outbox tracking (used by outbox service)

## Configuration

### Application Properties
- `server.port=8080` - Service port
- `spring.datasource.*` - PostgreSQL connection
- `crablet.eventstore.*` - EventStore configuration
- `resilience4j.*` - Circuit breaker and retry configuration

### Dependencies
- `crablet-eventstore` - Event sourcing library
- `shared-wallet-domain` - Event DTOs
- Spring Boot Web, JDBC, Actuator
- PostgreSQL driver, Flyway migrations

## Deployment

### Local Development
```bash
# Start PostgreSQL
make start-db

# Run service
make start

# Run tests
make test
```

### Docker Compose
```bash
cd wallet-eventstore-service
docker-compose up -d
```

### Kubernetes
```bash
kubectl apply -f ../kubernetes/eventstore-deployment.yaml
```

## Service Responsibilities

1. **Command Processing** - Handle wallet business operations
2. **Event Storage** - Store events in PostgreSQL
3. **State Projection** - Project current wallet state from events
4. **API Gateway** - Provide REST API for wallet operations
5. **Health Monitoring** - Expose health and metrics endpoints

## Performance Testing

This service includes comprehensive k6 performance tests to validate API performance under load.

### Running Performance Tests

```bash
# Navigate to performance tests directory
cd performance-tests

# Run all performance tests
./run-all-tests.sh

# Run specific test suites
./run-success-tests.sh      # Basic wallet operations
./run-concurrency-test.sh    # Concurrent transfers
./run-insufficient-test.sh   # Insufficient balance scenarios

# Run individual tests
k6 run simple-transfer-test.js
k6 run simple-concurrency-test.js
```

### Test Configuration

Tests run against `localhost:8080` (EventStore service port) and include:
- **Transfer Operations**: Money transfers between wallets
- **Concurrency Tests**: Concurrent operations with conflict resolution
- **Load Testing**: High-volume operations
- **Edge Cases**: Insufficient balance, invalid operations

### Test Data Strategy

Tests use pre-seeded wallet data with specific prefixes:
- `success_*` wallets for basic operations
- `concurrency_*` wallets for concurrent transfer tests
- `insufficient_*` wallets for error scenarios

See `performance-tests/README.md` for detailed test documentation.

## Integration with Outbox Service

This service writes events to the database. The `wallet-outbox-service` reads these events and publishes them to external systems.

Both services share the same PostgreSQL database but have different responsibilities:
- **EventStore Service**: Writes events, handles business logic
- **Outbox Service**: Reads events, publishes externally

