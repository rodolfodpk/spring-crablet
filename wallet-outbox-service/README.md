# Wallet Outbox Service

Microservice for reliable event publishing using the outbox pattern.

## Overview

This service reads events from PostgreSQL and publishes them to external systems using the outbox pattern. It provides a management API for monitoring and controlling the outbox processing.

## API Endpoints

### Management API
- `GET /api/outbox/publishers` - List all publishers and their status
- `POST /api/outbox/publishers/{name}/pause` - Pause a specific publisher
- `POST /api/outbox/publishers/{name}/resume` - Resume a specific publisher
- `GET /api/outbox/metrics` - Get outbox processing metrics

### Health & Metrics
- `GET /actuator/health` - Service health check
- `GET /actuator/metrics` - Service metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Architecture

### Outbox Pattern
- Reads events from PostgreSQL `events` table
- Processes events in batches using configurable publishers
- Tracks progress in `outbox_topic_progress` table
- Supports multiple lock strategies (GLOBAL, PER_TOPIC_PUBLISHER)

### Publishers
- `LogPublisher` - Logs events to console (development)
- `CountDownLatchPublisher` - Test publisher for integration tests
- `StatisticsPublisher` - Collects processing statistics
- `GlobalStatisticsPublisher` - Aggregates global statistics

## Configuration

### Application Properties
- `server.port=8081` - Service port
- `spring.datasource.*` - PostgreSQL connection
- `crablet.outbox.*` - Outbox configuration
- `crablet.outbox.topics.*` - Topic configuration
- `crablet.outbox.publishers.*` - Publisher configuration

### Dependencies
- `crablet-outbox` - Outbox pattern library
- `shared-wallet-domain` - Event DTOs
- Spring Boot Web, JDBC, Actuator
- Resilience4j, Micrometer

## Deployment

### Local Development
```bash
# Start PostgreSQL
make start-db

# Run service
make start

# Run tests
make test
```

### Docker Compose
```bash
cd wallet-outbox-service
docker-compose up -d
```

### Kubernetes
```bash
kubectl apply -f ../kubernetes/outbox-deployment.yaml
```

## Service Responsibilities

1. **Event Processing** - Read events from PostgreSQL
2. **Reliable Publishing** - Publish events to external systems
3. **Progress Tracking** - Track processing progress per topic
4. **Management API** - Provide control endpoints
5. **Monitoring** - Expose metrics and health checks

## Integration with EventStore Service

This service reads events written by the `wallet-eventstore-service`. Both services share the same PostgreSQL database but have different responsibilities:

- **EventStore Service**: Writes events, handles business logic
- **Outbox Service**: Reads events, publishes externally

## Observability

This service is monitored through the root-level observability stack that monitors both EventStore and Outbox services.

### Metrics
- Events processed per publisher
- Processing latency
- Error rates
- Publisher health status

### Logs
- Structured JSON logging
- Event processing details
- Error tracking

### Dashboards
- Grafana dashboards for monitoring both services
- Prometheus metrics collection
- Loki log aggregation

See [../observability/README.md](../observability/README.md) for setup instructions.

## Performance Testing

Performance testing is handled by the EventStore service since that's where the business API is tested. See [../wallet-eventstore-service/performance-tests/README.md](../wallet-eventstore-service/performance-tests/README.md) for details.
