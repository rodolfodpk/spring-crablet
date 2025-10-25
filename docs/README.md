# Documentation Index

Complete documentation for the Spring Crablet event sourcing library and wallet example application.

## For Developers

### API and Integration
- **[API Reference](api/README.md)** - REST endpoints, request/response schemas, examples
- **[Available URLs](urls.md)** - Complete reference of all service endpoints
- **[Development Guide](development/README.md)** - Local setup, testing strategies, coding practices

### Architecture and Design
- **[Architecture Overview](architecture/README.md)** - DCB pattern, event sourcing, system design
- **[DCB Technical Details](architecture/DCB_AND_CRABLET.md)** - Deep dive into Dynamic Consistency Boundary pattern
- **[Outbox Pattern](architecture/OUTBOX_PATTERN.md)** - Reliable event publishing implementation
- **[Outbox Pattern Rationale](architecture/OUTBOX_RATIONALE.md)** - Why and when to use the outbox pattern

### Testing
- **[Testing Guide](testing/README.md)** - Testing strategies and test configuration
- **[Test Configuration](testing/TEST_CONFIGURATION.md)** - Test profiles and environment setup
- **[Test Profile](testing/TEST_PROFILE.md)** - Profile-specific test configurations

## For Operations

### Deployment and Configuration
- **[Setup Guide](setup/README.md)** - Installation prerequisites and configuration
- **[Read Replicas](setup/READ_REPLICAS.md)** - PostgreSQL read replica setup
- **[PgBouncer Guide](setup/PGBOUNCER.md)** - Connection pooling configuration

### Monitoring and Operations
- **[Observability Overview](observability/README.md)** - Monitoring, metrics, and logging setup
- **[Getting Started](observability/getting-started.md)** - Quick start for observability stack
- **[Metrics Reference](observability/metrics-reference.md)** - Available metrics and their meaning
- **[Outbox Metrics](observability/OUTBOX_METRICS.md)** - Outbox-specific metrics
- **[Dashboards](observability/dashboards.md)** - Grafana dashboard configurations
- **[Alerting](observability/alerting.md)** - Alert rules and notification setup
- **[Logging](observability/logging.md)** - Structured logging with Loki
- **[Troubleshooting](observability/troubleshooting.md)** - Common issues and solutions

### Performance and Security
- **[Performance Testing](../wallet-eventstore-service/performance-tests/README.md)** - Load testing and benchmarks
- **[Security Guide](security/README.md)** - Rate limiting, HTTP/2, input validation

## Implementation Details

### Advanced Topics
- **[HTTP/2 Implementation](etc/HTTP2_IMPLEMENTATION.md)** - HTTP/2 configuration details
- **[Rate Limiting Implementation](etc/RATE_LIMITING_IMPLEMENTATION.md)** - Rate limiter implementation
- **[Performance Optimizations](etc/PERFORMANCE_OPTIMIZATIONS.md)** - Performance tuning strategies

## Quick Links

- [Main README](../README.md) - Project overview and quick start
- [API Examples](api/README.md#examples) - Common API usage patterns
- [Troubleshooting](observability/troubleshooting.md) - Debugging guide
