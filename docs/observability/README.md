# Observability Stack

The application includes a comprehensive observability stack with Prometheus (metrics), Grafana (dashboards), and Loki (logs) for monitoring, visualization, and log aggregation.

## Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spring Boot   │───▶│   Prometheus    │───▶│     Grafana     │
│   Application   │    │   (Metrics)     │    │  (Dashboards)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Promtail      │───▶│      Loki       │    │   Alerting      │
│  (Log Shipper)  │    │     (Logs)      │    │    Rules        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Components

### Prometheus
- **Purpose**: Metrics collection and storage
- **Access**: http://localhost:9090
- **Configuration**: `observability/prometheus/prometheus.yml`
- **Scrapes**: Spring Boot application metrics from `/actuator/prometheus`

### Grafana
- **Purpose**: Data visualization and dashboards
- **Access**: http://localhost:3000 (admin/admin)
- **Configuration**: 
  - Datasources: `observability/grafana/datasources/`
  - Dashboards: `observability/grafana/dashboards/`
  - Alerting: `observability/grafana/provisioning/alerting/`

### Loki
- **Purpose**: Log aggregation and storage
- **Access**: http://localhost:3100
- **Configuration**: `observability/loki/loki-config.yml`
- **Receives**: Application logs via Promtail

### Promtail
- **Purpose**: Log shipping agent
- **Configuration**: `observability/promtail/promtail-config.yaml`
- **Monitors**: `logs/application.log` directory

## Data Flow

1. **Metrics**: Spring Boot → Prometheus → Grafana
2. **Logs**: Application → Promtail → Loki → Grafana
3. **Alerts**: Grafana monitors metrics and triggers alerts

## Quick Start

```bash
# Start all services including observability stack
docker-compose up -d

# Access the services
# Grafana:    http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
# Loki:       http://localhost:3100
```

## Documentation

- [Getting Started](getting-started.md) - Installation and first-time setup
- [Dashboard Guide](dashboards.md) - Detailed dashboard descriptions
- [Metrics Reference](metrics-reference.md) - Complete metrics catalog
- [Alerting](alerting.md) - Alert rules and configuration
- [Logging](logging.md) - Log format and querying
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

## Quick Navigation

| Topic | Document | Description |
|-------|----------|-------------|
| **Setup** | [Getting Started](getting-started.md) | First-time installation and configuration |
| **Dashboards** | [Dashboard Guide](dashboards.md) | Understanding each dashboard |
| **Metrics** | [Metrics Reference](metrics-reference.md) | Complete metrics catalog |
| **Alerts** | [Alerting](alerting.md) | Alert configuration and management |
| **Logs** | [Logging](logging.md) | Log format and querying |
| **Issues** | [Troubleshooting](troubleshooting.md) | Common problems and solutions |

## Pre-configured Dashboards

| Dashboard | Path | Description |
|-----------|------|-------------|
| JVM & System | `/d/jvm-system` | Memory, GC, threads, CPU |
| Database | `/d/database` | HikariCP pool, connections, query performance |
| Application | `/d/application` | HTTP metrics, circuit breaker, retries |
| Business | `/d/business` | Wallet operations, transaction volumes |

## Key Features

- **Real-time Metrics**: JVM, database, application, and business metrics
- **Financial Monitoring**: Transaction volumes, wallet balances, failed amounts
- **Infrastructure Health**: Circuit breakers, connection pools, error rates
- **Structured Logging**: JSON format with trace/span IDs
- **Alerting**: Critical infrastructure and business alerts
- **Performance Monitoring**: Response times, throughput, resource usage
