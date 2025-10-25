# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Coverage](.github/badges/jacoco.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Branches](.github/badges/branches.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern with microservices architecture, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

**📚 [Project Background](docs/architecture/README.md)** | **📚 [Features](docs/architecture/README.md#features)** | **📚 [Architecture](docs/architecture/README.md)**

## Architecture

This project demonstrates a **microservices architecture** with separate EventStore and Outbox services:

### Services
- **wallet-eventstore-service** (Port 8080) - Business API for wallet operations
- **wallet-outbox-service** (Port 8081) - Reliable event publishing with outbox pattern
- **PostgreSQL** - Shared database for both services

### Libraries
- **crablet-eventstore** - Event sourcing library with Spring integration
- **crablet-outbox** - Outbox pattern library with Spring integration  
- **shared-wallet-domain** - Shared event DTOs for serialization contracts

## Quick Start

### Build and Test (No Application Required)
```bash
# Build and run all tests with Testcontainers
./mvnw clean install

# Or just run tests
./mvnw test verify
```

### Run Microservices Locally

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

**Available URLs after startup**:
- EventStore API: http://localhost:8080/api
- Outbox Management API: http://localhost:8081/api/outbox
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health Checks: http://localhost:8080/actuator/health, http://localhost:8081/actuator/health

### Kubernetes Deployment

```bash
# Deploy to Kubernetes
kubectl apply -f kubernetes/

# Check deployment
kubectl get pods
kubectl get services
```

See [kubernetes/README.md](kubernetes/README.md) for detailed deployment instructions.

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

**📚 [Detailed Setup](docs/setup/README.md)** | **📚 [API Reference](docs/api/README.md)** | **📚 [All URLs](docs/urls.md)**

## Key Components

- **DCB Pattern**: Cursor-based optimistic concurrency control
- **Event Sourcing**: Complete audit trail with state reconstruction
- **Java 25**: Records, sealed interfaces, virtual threads, pattern matching
- **Spring Boot**: REST API with PostgreSQL backend
- **Testing**: 553 tests (all passing) with Testcontainers
- **Observability**: Prometheus, Grafana, Loki monitoring stack (monitors both EventStore and Outbox services)

**📚 [DCB Technical Details](docs/architecture/DCB_AND_CRABLET.md)** | **📚 [Testing Guide](docs/development/README.md#testing-strategy)** | **📚 [Observability](docs/observability/README.md)**

## Documentation

- **[Architecture](docs/architecture/README.md)** - DCB pattern, event sourcing, system design
  - [Outbox Pattern](docs/architecture/OUTBOX_PATTERN.md) - Complete guide to reliable event publishing
  - [Outbox Pattern Rationale](docs/architecture/OUTBOX_RATIONALE.md) - Why and when to use the outbox pattern
- **[Development](docs/development/README.md)** - Setup, testing, coding practices
- **[API](docs/api/README.md)** - REST endpoints, examples, Swagger
- **[Security](docs/security/README.md)** - Rate limiting, HTTP/2, input validation
- **[Performance](wallet-eventstore-service/performance-tests/README.md)** - Load testing, benchmarks, optimization
- **[Observability](docs/observability/README.md)** - Monitoring, metrics, dashboards
- **[Setup](docs/setup/README.md)** - Installation and configuration
  - [Read Replicas](docs/setup/READ_REPLICAS.md) - PostgreSQL read replica configuration
  - [PgBouncer Guide](docs/setup/PGBOUNCER.md) - Connection pooling with PgBouncer
- **[Available URLs](docs/urls.md)** - Quick reference for all service URLs

## License

MIT License - see [LICENSE](LICENSE) file for details.
