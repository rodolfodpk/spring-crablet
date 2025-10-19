# Getting Started with Observability

This guide will help you set up and start using the observability stack for the first time.

## Prerequisites

- Docker and Docker Compose installed
- Spring Boot application running on port 8080
- Git repository cloned locally

## Installation Steps

### 1. Start the Observability Stack

```bash
# Start all services including observability stack
docker-compose up -d

# Verify all services are running
docker-compose ps
```

You should see the following services running:

- `postgres` - Database
- `prometheus` - Metrics collection
- `grafana` - Dashboards and visualization
- `loki` - Log aggregation
- `promtail` - Log shipping

### 2. Start the Spring Boot Application

```bash
# Start the application (in a separate terminal)
./mvnw spring-boot:run
```

The application will start on port 8080 and begin exposing metrics at `/actuator/prometheus`.

### 3. Access the Services

| Service        | URL                   | Credentials |
|----------------|-----------------------|-------------|
| **Grafana**    | http://localhost:3000 | admin/admin |
| **Prometheus** | http://localhost:9090 | -           |
| **Loki**       | http://localhost:3100 | -           |

## First-Time Setup

### 1. Verify Prometheus is Scraping Metrics

1. Open http://localhost:9090
2. Go to **Status** → **Targets**
3. Verify `java-crablet` target is **UP**
4. Click on the target to see scraped metrics

### 2. Verify Grafana Dashboards

1. Open http://localhost:3000
2. Login with `admin/admin`
3. Go to **Dashboards** (📊 icon in left sidebar)
4. You should see 4 pre-configured dashboards:
    - JVM & System Metrics
    - Database & HikariCP Metrics
    - Application & Resilience4j Metrics
    - Business Metrics - Wallet Operations

### 3. Generate Some Metrics

To see data in the dashboards, generate some application load:

```bash
# Run a simple load test (if k6 is installed)
cd performance-tests
k6 run deposit-test.js --duration 30s --vus 5
```

Or manually make some API calls:

See [API Reference](../api/README.md) for complete API examples and usage.

## Basic Navigation

### Grafana Interface

1. **Dashboards**: View pre-configured dashboards
2. **Explore**: Query metrics and logs directly
3. **Alerting**: View and manage alerts
4. **Configuration**: Manage datasources and settings

### Prometheus Interface

1. **Graph**: Query and visualize metrics
2. **Status** → **Targets**: Check scraped targets
3. **Status** → **Rules**: View alerting rules
4. **Alerts**: View active alerts

### Key Metrics to Watch

- **JVM Memory**: `jvm_memory_used_bytes`
- **HTTP Requests**: `http_server_requests_seconds_count`
- **Database Connections**: `hikaricp_connections_active`
- **Wallet Operations**: `wallet_operations_duration_seconds_count`

## Next Steps

- [Dashboard Guide](dashboards.md) - Learn about each dashboard
- [Metrics Reference](metrics-reference.md) - Complete metrics catalog
- [Alerting](alerting.md) - Configure alerts
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

## Related Documentation

- [Complete Observability Guide](README.md) - Overview and architecture
- [Dashboard Guide](dashboards.md) - Detailed dashboard descriptions
- [Metrics Reference](metrics-reference.md) - Available metrics
- [Alerting](alerting.md) - Alert configuration
- [Logging](logging.md) - Log format and querying
- [Troubleshooting](troubleshooting.md) - Common issues

## Quick Commands

```bash
# Check service status
docker-compose ps

# View logs
docker-compose logs grafana
docker-compose logs prometheus

# Restart services
docker-compose restart grafana prometheus

# Stop services
docker-compose down
```
