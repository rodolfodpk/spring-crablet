# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Coverage](.github/badges/jacoco.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Branches](.github/badges/branches.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Project Background

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go). Built with Spring Boot to explore event sourcing in Java ecosystem.

**📚 [Why Event Sourcing?](docs/architecture/README.md#event-sourcing-benefits)** | **📚 [Development Approach](docs/development/README.md)** | **📚 [Package Organization](docs/architecture/README.md#package-organization)**

## Features

- **Java 25**: Records, sealed interfaces, virtual threads, and pattern matching
- **Spring Boot**: Spring Boot 3.5 with Undertow and JDBC PostgreSQL
- **DCB Pattern**: Dynamic Consistency Boundary for event sourcing
- **Type Safety**: Generic projections with compile-time validation
- **Comprehensive Testing**: Unit and integration test separation
- **Wallet Domain**: Complete event sourcing implementation
- **Observability**: Prometheus, Grafana, and Loki monitoring stack

## Quick Start

### Prerequisites

- Java 25
- Docker & Docker Compose
- Maven 3.9+
- k6 (for performance testing)

### Installation

```bash
# Clone the repository
git clone <repository-url>
cd wallets-challenge

# Start everything (PostgreSQL + Spring Boot)
make start

# Run a quick performance test
make perf-quick

# Check application health
make health

# Stop everything
make stop
```

### Manual Setup

```bash
# 1. Start PostgreSQL
docker-compose up -d postgres

# 2. Start the application
./mvnw spring-boot:run

# 3. Access Swagger UI
open http://localhost:8080/swagger-ui/index.html
```

## API

The wallet service provides RESTful endpoints for wallet operations. For complete API documentation with examples,
see [API Reference](docs/api/README.md).

## Observability

The application includes a comprehensive observability stack with Prometheus (metrics), Grafana (dashboards), and Loki (
logs). For detailed setup and configuration, see [Observability Guide](docs/observability/README.md).

## Documentation

See [docs/](docs/) for additional documentation on architecture, development, and observability.

## Testing

**435 tests passing** (369 unit + 66 integration) with Testcontainers infrastructure.

```bash
./mvnw test verify    # All tests
./mvnw test          # Unit tests only
./mvnw verify        # Integration tests only
```

**📚 [Testing Guide](docs/development/README.md#testing-strategy)** | **📚 [Performance Tests](performance-tests/README.md)**

## Configuration

For detailed configuration options, see [Setup Guide](docs/setup/README.md#configuration).

## Architecture

The application implements **event sourcing** with the **Dynamic Consistency Boundary (DCB)** pattern for scalable,
consistent wallet operations. For detailed architecture documentation,
see [Architecture Guide](docs/architecture/README.md).

### Dynamic Consistency Boundary (DCB)

DCB is an optimistic concurrency control pattern for event sourcing using cursor-based, entity-scoped conflict detection. Prevents double-spending and inconsistent state through position-based checks before event appends.

**📚 [Technical Details →](docs/architecture/DCB_AND_CRABLET.md)**

**Key characteristics**: Entity-scoped conflicts, cursor-based tracking, PostgreSQL-native implementation, 700+ req/s throughput.

## Performance

k6-based performance tests with verified results (October 2025):

- **Wallet Creation**: 6,288 operations, 111ms p95, 0.31% error rate
- **Deposits**: 16,804 operations, 41ms p95, 0% error rate
- **Throughput**: 110-336 req/s depending on operation

**📊 [Complete Results](performance-tests/results/summary.md)** | **📚 [Performance Guide](performance-tests/README.md)**

## Security

⚠️ **Experimental project** - Missing production security features (authentication, authorization, HTTPS) for educational purposes.

**Implemented**: Input validation, SQL injection prevention, audit logging via event sourcing.

**📚 [Security Details](docs/setup/README.md#security)**

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
