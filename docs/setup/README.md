# Setup Guide

Installation and configuration for the wallet challenge solution.

## Prerequisites

- **Java 25**: [Installation guide](https://adoptium.net/temurin/releases/)
- **Maven 3.9+**: [Installation guide](https://maven.apache.org/install.html)
- **Docker & Docker Compose**: [Installation guide](https://docs.docker.com/get-docker/)
- **k6** (optional): [Installation guide](https://k6.io/docs/getting-started/installation/)

### Quick Installation (macOS)

```bash
# Install all prerequisites
brew install openjdk@25 maven k6
brew install --cask docker

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

## Quick Start

### Run the Application
```bash
# Clone repository
git clone <repository-url>
cd spring-crablet

# Start all services (PostgreSQL, Prometheus, Grafana, Loki, Promtail)
make start

# Verify setup
make health

# Access Swagger UI
open http://localhost:8080/swagger-ui/index.html
```

See [docs/urls.md](../urls.md) for all available URLs.

### Build and Test (No Application Required)

```bash
# Build and test using Testcontainers
./mvnw clean install

# Tests use Testcontainers - no need to start PostgreSQL or Spring Boot
```

## Configuration

### Application Properties

```properties
# src/main/resources/application.properties
server.port=8080
spring.application.name=wallets-challenge

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/crablet
spring.datasource.username=crablet
spring.datasource.password=crablet

# Connection Pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

### Environment Profiles

```bash
# Development
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production
java -jar target/wallets-challenge-1.0.0.jar --spring.profiles.active=prod
```

## Development

### Local Development

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Run application
./mvnw spring-boot:run

# Run tests
./mvnw test verify
```

### IDE Setup

- **IntelliJ IDEA**: Import project, configure Java 25 SDK
- **VS Code**: Install Java Extension Pack, open project folder

## Verification

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check database
docker-compose exec postgres pg_isready -U crablet -d crablet

# Run performance tests
cd performance-tests && ./run-all-tests.sh
```

## Troubleshooting

### Common Issues

**Java Version**: Ensure Java 25 is installed and JAVA_HOME is set

```bash
java --version
export JAVA_HOME=$(/usr/libexec/java_home -v 25)  # macOS
```

**Database Connection**: Reset database if needed

```bash
docker-compose down -v
docker-compose up -d postgres
```

**Maven Issues**: Clean and rebuild

```bash
./mvnw clean install -U
```

## Related Documentation

- [Architecture](../architecture/README.md) - System design
- [API Reference](../api/README.md) - API documentation
- [Development Guide](../development/README.md) - Development practices
- [Observability](../observability/README.md) - Monitoring setup
- [Read Replicas](./READ_REPLICAS.md) - PostgreSQL read replica configuration
- [PgBouncer Guide](./PGBOUNCER.md) - Using PgBouncer for connection pooling
