# Metrics Reference

Key metrics exposed by the application for monitoring and alerting.

## Wallet Operations

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `wallet_operations_duration_seconds_count` | Counter | Total operations by type | `operation` |
| `wallet_operations_duration_seconds_sum` | Counter | Total operation time | `operation` |
| `wallet_operations_failures_total` | Counter | Failed operations by reason | `operation`, `reason` |
| `wallet_transaction_failed_amount_total` | Counter | Failed transaction amounts | `operation` |
| `wallet_concurrency_conflicts` | Counter | Optimistic locking failures | - |

## Financial Metrics

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `wallet_transaction_volume` | Counter | Total money moved | `operation` |
| `wallet_transaction_failed_amount` | Counter | Money that failed to move | `operation` |
| `wallet_transaction_large_count` | Counter | Large transactions (>$10k) | `operation` |
| `wallet_balance_total` | Gauge | Sum of all wallet balances | - |

## Event Store

| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `eventstore_operations_total` | Counter | Event store operations | `operation` |
| `eventstore_events_appended` | Counter | Total events written | - |
| `eventstore_concurrency_violations` | Counter | Event store conflicts | - |

## Infrastructure Metrics

### JVM
| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `jvm_memory_used_bytes` | Gauge | Memory usage by area | `area` |
| `jvm_memory_max_bytes` | Gauge | Maximum memory by area | `area` |
| `jvm_gc_pause_seconds_sum` | Counter | Total GC pause time | `gc` |
| `jvm_threads_live_threads` | Gauge | Current thread count | - |

### Database (HikariCP)
| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `hikaricp_connections_active` | Gauge | Active connections | - |
| `hikaricp_connections_idle` | Gauge | Idle connections | - |
| `hikaricp_connections_pending` | Gauge | Pending connection requests | - |
| `hikaricp_connections_max` | Gauge | Maximum pool size | - |

### HTTP
| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `http_server_requests_seconds_count` | Counter | Total requests | `method`, `uri`, `status` |
| `http_server_requests_seconds_sum` | Counter | Total request time | `method`, `uri`, `status` |

### Resilience4j
| Metric | Type | Description | Tags |
|--------|------|-------------|------|
| `resilience4j_circuitbreaker_state` | Gauge | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) | `name` |
| `resilience4j_circuitbreaker_failure_rate` | Gauge | Failure rate percentage | `name` |

## Common Tags

- `operation`: `deposit`, `withdraw`, `transfer`, `open_wallet`
- `status`: HTTP status codes (`200`, `400`, `500`)
- `method`: HTTP methods (`GET`, `POST`, `PUT`, `DELETE`)
- `area`: Memory areas (`heap`, `nonheap`)
- `name`: Resilience4j component name (`database`)

## Query Examples

```promql
# Request rate per second
rate(http_server_requests_seconds_count[5m])

# Error rate percentage
rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) * 100

# 95th percentile response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

## Related Documentation

- [Observability Guide](README.md) - Overview
- [Dashboard Guide](dashboards.md) - Using metrics in dashboards
- [Alerting](alerting.md) - Alert configuration
