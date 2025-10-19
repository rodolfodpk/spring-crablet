# Dashboard Guide

Pre-configured Grafana dashboards for monitoring the application.

## Dashboard Overview

| Dashboard    | Path             | Focus Area           | Key Metrics                            |
|--------------|------------------|----------------------|----------------------------------------|
| JVM & System | `/d/jvm-system`  | Runtime performance  | Memory, GC, threads, CPU               |
| Database     | `/d/database`    | Database health      | Connection pool, query performance     |
| Application  | `/d/application` | HTTP and resilience  | Request rates, circuit breakers        |
| Business     | `/d/business`    | Financial operations | Wallet operations, transaction volumes |

## JVM & System Dashboard

**Path**: `/d/jvm-system/jvm-and-system-metrics`

**Panels**:

- **Heap Memory**: Current and maximum heap usage by area
- **Threads**: Live and daemon thread counts
- **Garbage Collection**: GC pause times and frequency
- **CPU Usage**: System and process CPU utilization

**Key Metrics**: `jvm_memory_used_bytes`, `jvm_threads_live_threads`, `jvm_gc_pause_seconds_sum`

## Database Dashboard

**Path**: `/d/database/database-and-hikaricp-metrics`

**Panels**:

- **Connection Pool**: Active, idle, and pending connections
- **Pool Performance**: Connection creation and usage times
- **Pool Configuration**: Min/max pool sizes
- **Connection Errors**: Timeout and validation failures

**Key Metrics**: `hikaricp_connections_active`, `hikaricp_connections_pending`

## Application Dashboard

**Path**: `/d/application/application-and-resilience4j-metrics`

**Panels**:

- **HTTP Request Rate**: Requests per second by endpoint
- **HTTP Latency**: Response time percentiles (50th, 95th, 99th)
- **Circuit Breaker**: State and failure rates
- **Error Rates**: 4xx and 5xx error percentages

**Key Metrics**: `http_server_requests_seconds_count`, `resilience4j_circuitbreaker_state`

## Business Dashboard

**Path**: `/d/business/business-metrics-wallet-operations`

**Panels**:

- **Wallet Operations**: Operations per second by type
- **Financial Overview**: Total system balance and averages
- **Transaction Volume**: Money moved and failed amounts
- **Concurrency Conflicts**: Optimistic locking failures

**Key Metrics**: `wallet_operations_duration_seconds_count`, `wallet_transaction_failed_amount_total`

## Related Documentation

- [Observability Guide](README.md) - Overview
- [Metrics Reference](metrics-reference.md) - Available metrics
- [Alerting](alerting.md) - Alert configuration
