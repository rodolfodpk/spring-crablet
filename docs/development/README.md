# Development Guide

Development practices and testing strategies for the wallet challenge solution.

## Development Setup

### Prerequisites

- **Java 25**: [Installation guide](https://adoptium.net/temurin/releases/)
- **Maven 3.9+**: [Installation guide](https://maven.apache.org/install.html)
- **Docker & Docker Compose**: [Installation guide](https://docs.docker.com/get-docker/)

### Local Development

#### Building and Testing
```bash
# Build and test (uses Testcontainers, no app startup needed)
./mvnw clean compile
./mvnw test verify
./mvnw package
```

Tests use Testcontainers and don't require PostgreSQL or Spring Boot to be running.

#### Running the Application

```bash
# Option 1: Using make
make start

# Option 2: Manual start
docker-compose up -d  # Starts PostgreSQL + observability stack
./mvnw spring-boot:run

# Verify
curl http://localhost:8080/actuator/health
```

## Project Structure

```
src/
├── main/java/com/wallets/
│   ├── Application.java                 # Main application class
│   ├── config/                         # Configuration classes
│   ├── crablet/                        # DCB library implementation
│   ├── domain/                         # Domain models (commands, events, handlers)
│   └── service/                        # Service layer
└── test/java/com/wallets/
    ├── crablet/                        # Unit tests
    ├── domain/                         # Domain tests
    └── service/                        # Service tests
```

## Testing Strategy

### Test Categories

#### Unit Tests (369 tests)

- **Location**: `src/test/java/com/wallets/`
- **Duration**: < 1 second per test
- **Scope**: Isolated component testing
- **Examples**: Command handlers, domain logic, state projections

#### Integration Tests (66 tests)

- **Controller ITs**: Individual endpoint testing (44 tests)
- **Integration ITs**: End-to-end workflow testing (13 tests)
- **Domain ITs**: Business logic integration testing (9 tests)
- **Infrastructure**: PostgreSQL via Testcontainers
- **Base Class**: All tests extend `AbstractCrabletTest`

### Test Execution

```bash
# Run all tests
./mvnw test verify

# Run specific test class
./mvnw test -Dtest=WalletServiceTest

# Run integration tests
./mvnw test -Dtest="*ControllerIT"

# Generate coverage report
./mvnw test jacoco:report
```

### Test Infrastructure

- **Testcontainers PostgreSQL**: Shared container across all tests
- **Automatic Cleanup**: Database truncated between tests for isolation
- **Base Class**: All integration tests extend `AbstractCrabletTest`

## Code Quality

### Coverage Goals

- **Line Coverage**: > 60% (currently 71%)
- **JaCoCo**: Code coverage analysis

## Architecture Constraints

### Crablet Package Rules

Enforced by `CrabletArchitectureTest`:

- **`crablet.core`**: Interfaces only, no Spring dependencies
- **`crablet.impl`**: Spring implementations with `@Component`

Run: `./mvnw test -Dtest=CrabletArchitectureTest`

### Code Formatting

- **EditorConfig**: Standard formatting rules
- **IntelliJ IDEA**: Java → Google Style

## Development Practices

### Git Workflow

- **Branch Strategy**: main, develop, feature/, hotfix/, release/
- **Commit Messages**: `type(scope): description`
- **Pull Request Process**: Feature branch → Tests → Review → Merge

### Debugging

#### Local Debugging

- **IntelliJ IDEA**: Set breakpoints, debug configuration
- **VS Code**: Java Extension Pack, F5 to debug

#### Remote Debugging

```bash
# Enable remote debugging
export JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
./mvnw spring-boot:run
```

## Troubleshooting

### Common Issues

**Build Issues**: Clean Maven cache and rebuild

```bash
./mvnw clean install -U
```

**Test Issues**: Check Docker is running for Testcontainers

```bash
docker ps
./mvnw test -X
```

**Database Issues**: Reset database if needed

```bash
docker-compose down -v
docker-compose up -d postgres
```

## Related Documentation

- [Architecture](../architecture/README.md) - System design
- [API Reference](../api/README.md) - API documentation
- [Setup Guide](../setup/README.md) - Installation
- [Observability](../observability/README.md) - Monitoring
