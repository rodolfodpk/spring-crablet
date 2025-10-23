# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Coverage](.github/badges/jacoco.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Branches](.github/badges/branches.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

**📚 [Project Background](docs/architecture/README.md)** | **📚 [Features](docs/architecture/README.md#features)** | **📚 [Architecture](docs/architecture/README.md)**

## Quick Start

### Build and Test (No Application Required)
```bash
# Build and run all tests with Testcontainers
./mvnw clean install

# Or just run tests
./mvnw test verify
```

### Run the Application

```bash
# Start all services (PostgreSQL, Prometheus, Grafana, Loki, Promtail)
docker-compose up -d

# Start Spring Boot application
./mvnw spring-boot:run

# Or use make (does both)
make start
```

**Available URLs after startup**:
- API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090

See [docs/urls.md](docs/urls.md) for complete URL reference.

### Performance Testing (Requires Running Application)

```bash
make start
make perf-quick
make stop
```

**📚 [Detailed Setup](docs/setup/README.md)** | **📚 [API Reference](docs/api/README.md)** | **📚 [All URLs](docs/urls.md)**

## Key Components

- **DCB Pattern**: Cursor-based optimistic concurrency control
- **Event Sourcing**: Complete audit trail with state reconstruction
- **Java 25**: Records, sealed interfaces, virtual threads, pattern matching
- **Spring Boot**: REST API with PostgreSQL backend
- **Testing**: 435 tests (369 unit + 66 integration) with Testcontainers
- **Observability**: Prometheus, Grafana, Loki monitoring stack

**📚 [DCB Technical Details](docs/architecture/DCB_AND_CRABLET.md)** | **📚 [Testing Guide](docs/development/README.md#testing-strategy)** | **📚 [Observability](docs/observability/README.md)**

## Documentation

- **[Architecture](docs/architecture/README.md)** - DCB pattern, event sourcing, system design
  - [Outbox Pattern Rationale](docs/architecture/OUTBOX_RATIONALE.md) - Optional API for reliable event publishing to external systems
- **[Development](docs/development/README.md)** - Setup, testing, coding practices
- **[API](docs/api/README.md)** - REST endpoints, examples, Swagger
- **[Security](docs/security/README.md)** - Rate limiting, HTTP/2, input validation
- **[Performance](performance-tests/README.md)** - Load testing, benchmarks, optimization
- **[Observability](docs/observability/README.md)** - Monitoring, metrics, dashboards
- **[Available URLs](docs/urls.md)** - Quick reference for all service URLs

## License

MIT License - see [LICENSE](LICENSE) file for details.
