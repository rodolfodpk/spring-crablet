# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern with microservices architecture, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Prerequisites

- Java 25 ([Temurin](https://adoptium.net/) recommended)
- Maven 3.9+
- Docker and Docker Compose
- PostgreSQL 17+ (or use Docker Compose)

[Architecture](docs/architecture/README.md) | [API Reference](docs/api/README.md) | [Development Guide](docs/development/README.md)

## Architecture

This project demonstrates a **microservices architecture** with separate EventStore and Outbox services:

### Services
- **wallet-eventstore-service** (Port 8080) - Business API for wallet operations
- **wallet-outbox-service** (Port 8081) - Reliable event publishing with outbox pattern
- **PostgreSQL** - Shared database for both services

### Libraries
- **crablet-eventstore** - Event sourcing library with Spring integration
- **crablet-outbox** - Outbox pattern library with Spring integration  
- **shared-wallet-domain** - Shared wallet domain logic including events, projectors, exceptions, and constants

## Quick Start

### Build and Test
Tests use Testcontainers (no external dependencies required):
```bash
./mvnw clean install
```

### Run Services Locally

```bash
# Start PostgreSQL
docker-compose up -d

# Start EventStore service
cd wallet-eventstore-service
./mvnw spring-boot:run

# In another terminal, start Outbox service
cd wallet-outbox-service
./mvnw spring-boot:run
```

Services will start on:
- **EventStore API**: http://localhost:8080/api
- **Outbox Management API**: http://localhost:8081/api/outbox
- **Swagger UI (EventStore)**: http://localhost:8080/swagger-ui/index.html
- **Swagger UI (Outbox)**: http://localhost:8081/swagger-ui/index.html

See [docs/urls.md](docs/urls.md) for complete URL reference.

### Performance Testing

Performance tests are located in `wallet-eventstore-service/performance-tests/` and test the EventStore API:

```bash
# Navigate to performance tests
cd wallet-eventstore-service/performance-tests

# Run all performance tests
./run-all-tests.sh

# Run specific test suites
./run-success-tests.sh      # Basic wallet operations
./run-concurrency-test.sh    # Concurrent transfers
```

See [wallet-eventstore-service/performance-tests/README.md](wallet-eventstore-service/performance-tests/README.md) for detailed documentation.

## Key Components

- **DCB Pattern**: Cursor-based optimistic concurrency control
- **Event Sourcing**: Complete audit trail with state reconstruction
- **Java 25**: Records, sealed interfaces, virtual threads, pattern matching
- **Spring Boot**: REST API with PostgreSQL backend
- **Testing**: 553 tests (all passing) with Testcontainers
- **Observability**: Prometheus, Grafana, Loki monitoring stack (monitors both EventStore and Outbox services)

## Documentation

### For Developers
- **[API Reference](docs/api/README.md)** - REST endpoints, request/response examples
- **[Development Guide](docs/development/README.md)** - Setup, testing, coding practices
- **[Architecture](docs/architecture/README.md)** - DCB pattern, event sourcing, system design
- **[Available URLs](docs/urls.md)** - Quick reference for all service endpoints

### For Operations
- **[Setup](docs/setup/README.md)** - Installation and configuration
- **[Observability](docs/observability/README.md)** - Monitoring, metrics, dashboards  
- **[Performance Testing](wallet-eventstore-service/performance-tests/README.md)** - Load testing and benchmarks
- **[Security](docs/security/README.md)** - Rate limiting, HTTP/2, input validation

### Advanced Topics
- [Outbox Pattern](docs/architecture/OUTBOX_PATTERN.md) - Reliable event publishing
- [Read Replicas](docs/setup/READ_REPLICAS.md) - PostgreSQL read replica configuration
- [PgBouncer Guide](docs/setup/PGBOUNCER.md) - Connection pooling with PgBouncer

## License

MIT License - see [LICENSE](LICENSE) file for details.
