# Wallet Example

Example wallet application demonstrating Crablet event sourcing capabilities.

## Overview

The wallet example is a complete, production-ready application showcasing how to use Crablet for event sourcing. It implements a simple wallet domain with deposits, withdrawals, and transfers.

## Features

- **Full Event Sourcing**: All state changes stored as events
- **DCB Pattern**: Optimistic concurrency control using cursors
- **Transactional Outbox**: Reliable event publishing
- **REST API**: Complete REST API for wallet operations
- **Performance Tests**: k6 performance tests demonstrating scalability
- **Observability**: Full observability stack (Prometheus, Grafana, Loki)

## Quick Start

### Prerequisites

- Java 25
- Docker and Docker Compose
- k6 (for performance tests)

### Start the Application

```bash
# Start PostgreSQL and application
make start

# Or start with test profile (no outbox)
make start-test
```

### API Endpoints

- **Create Wallet**: `PUT /api/wallets/{id}`
- **Deposit**: `POST /api/wallets/{id}/deposit`
- **Withdraw**: `POST /api/wallets/{id}/withdraw`
- **Transfer**: `POST /api/wallets/{id}/transfer`
- **Get Balance**: `GET /api/wallets/{id}`
- **Get History**: `GET /api/wallets/{id}/history`

### Swagger UI

Access the interactive API documentation:

```
http://localhost:8080/swagger-ui/index.html
```

## Architecture

### Domain Events

- `WalletOpened` - New wallet created
- `DepositMade` - Money deposited
- `WithdrawalMade` - Money withdrawn
- `MoneyTransferred` - Money transferred between wallets

### Command Handlers

Command handlers enforce business rules and produce events:

- `OpenWalletCommandHandler` - Creates new wallets
- `DepositCommandHandler` - Processes deposits
- `WithdrawCommandHandler` - Processes withdrawals
- `TransferMoneyCommandHandler` - Processes transfers

### State Projection

State is projected from events:

- `WalletBalanceProjector` - Calculates current balance
- `WalletStateProjector` - Projects complete wallet state

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

### Performance Tests

```bash
# Run all performance tests
make perf-test

# Quick test (wallet creation only)
make perf-quick
```

## Performance Benchmarks

See [performance test results](performance-tests/k6-performance-test-results.md) for detailed benchmarks:

- **Wallet Creation**: ~1000 req/s
- **Transfers**: ~800 req/s
- **Concurrent Operations**: Handles DCB conflicts gracefully

## Observability

The application includes a full observability stack:

### Start Observability Stack

```bash
cd observability && ./setup.sh
```

### Access Dashboards

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Loki**: http://localhost:3100

### Metrics

Metrics are exposed at `/actuator/metrics`:

- `http.server.requests` - HTTP request metrics
- `crablet.outbox.events.published` - Outbox metrics
- `jvm.memory.used` - JVM metrics

## Makefile Commands

```bash
make start      # Start application
make start-test # Start with test profile
make stop       # Stop all services
make logs       # View application logs
make health     # Check application health
make perf-test  # Run performance tests
```

## Documentation

- [Architecture](docs/architecture/) - Detailed architecture documentation
- [Development](docs/development/) - Development guide
- [Performance](docs/performance/) - Performance optimization guide
- [Observability](docs/observability/) - Monitoring and observability
- [Testing](docs/testing/) - Testing strategies

## Project Structure

```
wallet-example/
├── src/main/java/com/wallets/
│   ├── domain/          # Domain events and projections
│   ├── features/        # Feature slices (deposit, withdraw, transfer)
│   └── infrastructure/ # Infrastructure (config, web, resilience)
├── src/test/java/       # All tests
├── performance-tests/   # k6 performance tests
├── observability/       # Prometheus, Grafana, Loki configs
└── docs/                # Documentation
```

## License

MIT

