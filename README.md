# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Coverage](.github/badges/jacoco.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Branches](.github/badges/branches.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

**📚 [Project Background](docs/architecture/README.md)** | **📚 [Features](docs/architecture/README.md#features)** | **📚 [Architecture](docs/architecture/README.md)**

## Quick Start

```bash
# Start everything (PostgreSQL + Spring Boot)
make start

# Run tests
./mvnw test verify

# Performance test
make perf-quick

# Stop everything
make stop
```

**📚 [Detailed Setup](docs/setup/README.md)** | **📚 [API Reference](docs/api/README.md)** | **📚 [Swagger UI](http://localhost:8080/swagger-ui/index.html)**

## Key Components

- **DCB Pattern**: Cursor-based optimistic concurrency control
- **Event Sourcing**: Complete audit trail with state reconstruction
- **Java 25**: Records, sealed interfaces, virtual threads, pattern matching
- **Spring Boot**: REST API with PostgreSQL backend
- **Testing**: 435 tests (369 unit + 66 integration) with Testcontainers
- **Observability**: Prometheus, Grafana, Loki monitoring stack

**📚 [DCB Technical Details](docs/architecture/DCB_AND_CRABLET.md)** | **📚 [Testing Guide](docs/development/README.md#testing-strategy)** | **📚 [Observability](docs/observability/README.md)**

## Performance

Verified results (October 2025): **723 req/s** wallet creation, **224 req/s** transfers, **zero false positives** in conflict detection.

**📊 [Complete Results](performance-tests/results/summary.md)** | **📚 [Performance Guide](performance-tests/README.md)**

## Security

⚠️ **Experimental project** - Missing production security features for educational purposes.

**📚 [Security Details](docs/setup/README.md#security)**

## Documentation

- **[Architecture](docs/architecture/README.md)** - DCB pattern, event sourcing, system design
- **[Development](docs/development/README.md)** - Setup, testing, coding practices
- **[API](docs/api/README.md)** - REST endpoints, examples, Swagger
- **[Observability](docs/observability/README.md)** - Monitoring, metrics, dashboards
- **[Performance](performance-tests/README.md)** - Load testing, benchmarks, optimization

## License

MIT License - see [LICENSE](LICENSE) file for details.
