# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Coverage](.github/badges/jacoco.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Branches](.github/badges/branches.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Project Background

This project represents a **Java 25 migration** of my original **crablet** event sourcing library, which was initially implemented in Kotlin. After building both [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go), I decided to port the DCB pattern to Java using Spring Boot to explore event sourcing in a different ecosystem.

### Why Event Sourcing?

I chose event sourcing for this challenge because it provides:
- **Complete audit trail** of all business operations
- **Temporal queries** - ability to reconstruct state at any point in time
- **Scalability** through event-driven architecture
- **Domain modeling** that closely matches business processes
- **Consistency guarantees** through the DCB pattern

### Development Approach

This implementation was developed using **Cursor IDE** with AI assistance, taking approximately **20+ hours** of development time. The AI helped with:
- Code generation and boilerplate reduction
- Architecture decisions and pattern implementation
- Testing strategies and performance optimization
- Documentation and configuration management

### Package Organization: Feature-Based Slices

The project organizes code by business capabilities rather than technical layers. Each feature slice (deposit, withdraw, transfer, openwallet) contains:
- **Controllers** - HTTP endpoints and request/response handling
- **Commands** - Business operations and validation
- **Handlers** - Business logic, event generation, and command-specific projections

This approach ensures high cohesion within each business capability while maintaining loose coupling between different features.

The final result demonstrates modern Java features (records, sealed interfaces, virtual threads, pattern matching), Spring Boot best practices, feature-based package organization, and comprehensive observability.

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

The wallet service provides RESTful endpoints for wallet operations. For complete API documentation with examples, see [API Reference](docs/api/README.md).

## Observability

The application includes a comprehensive observability stack with Prometheus (metrics), Grafana (dashboards), and Loki (logs). For detailed setup and configuration, see [Observability Guide](docs/observability/README.md).

## Documentation

See [docs/](docs/) for additional documentation on architecture, development, and observability.

## Testing

### Quick Start
```bash
# Run all tests (435 total: 369 unit + 66 integration)
./mvnw test verify

# Unit tests only (fast)
./mvnw test

# Integration tests only (full stack with Testcontainers)
./mvnw verify

# Run specific integration test
./mvnw test -Dtest=WalletWorkflowIT
```

### Test Status
- ✅ **435 tests passing** (369 unit + 66 integration)
- ✅ **All integration tests fixed** (October 2025)
- 🐳 **Testcontainers** infrastructure for isolated testing

📖 **Detailed testing guide**: [Development Guide](docs/development/README.md#testing-strategy)

### Performance Tests
```bash
# Start application with TEST profile (rate limiting disabled)
make start-test

# Run working performance tests
cd performance-tests
k6 run wallet-creation-load.js    # 6,288 operations, 111ms p95, 0.31% error rate
k6 run simple-deposit-test.js     # 16,804 operations, 41ms p95, 0% error rate

# Or use the automated runner (automatically uses test profile)
make perf-quick  # Quick wallet creation test
```

⚠️ **Important**: Always use the TEST profile when running performance tests to disable rate limiting. See [Test Profile Documentation](docs/testing/TEST_PROFILE.md) for details.

## Configuration

For detailed configuration options, see [Setup Guide](docs/setup/README.md#configuration).


## Architecture

The application implements **event sourcing** with the **Dynamic Consistency Boundary (DCB)** pattern for scalable, consistent wallet operations. For detailed architecture documentation, see [Architecture Guide](docs/architecture/README.md).

## Performance

k6-based performance tests are included in `performance-tests/`:

### Verified Results (October 2025)
- **Wallet Creation**: 6,288 operations, 111ms p95 response time, 0.31% error rate
- **Deposit Operations**: 16,804 operations, 41ms p95 response time, 0% error rate
- **Throughput**: 110-336 requests/second depending on operation type

**📊 [View Complete Performance Results](performance-tests/results/summary.md)**

### Working Tests
- `wallet-creation-load.js` - Concurrent wallet creation (20 users, 50s)
- `simple-deposit-test.js` - High-frequency deposits (10 users, 50s)

Run tests: `make start-test && cd performance-tests && k6 run wallet-creation-load.js`

⚠️ **Note**: Performance tests require the TEST profile to disable rate limiting. See [Test Profile Documentation](docs/testing/TEST_PROFILE.md).

## Security

> **⚠️ Experimental Project Disclaimer**: This is an **experimental/learning project** focused on exploring event sourcing patterns and modern Java features. The missing production security features listed below are **intentionally omitted** for simplicity and are acceptable for this educational context.

### Implemented
- **Input Validation**: Spring Boot validation annotations
- **SQL Injection Prevention**: Parameterized queries
- **Audit Logging**: Event sourcing provides comprehensive audit trails

### TODO - Production Requirements
| Feature | Status | Priority |
|---------|--------|----------|
| Authentication | ❌ Not implemented | High |
| Authorization | ❌ Not implemented | High |
| HTTPS | ❌ Not implemented | High |
| Rate Limiting | ❌ Not implemented | Medium |
| CORS Configuration | ❌ Not implemented | Medium |

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
