# Alerting Configuration

Grafana alerting rules for monitoring critical infrastructure and business metrics.

## Alert Rules

### Infrastructure Alerts

| Alert                     | Metric                                                                                                       | Threshold | Duration | Severity |
|---------------------------|--------------------------------------------------------------------------------------------------------------|-----------|----------|----------|
| Circuit Breaker Open      | `resilience4j_circuitbreaker_state{name="database"} == 1`                                                    | 1         | 1m       | Critical |
| Connection Pool Exhausted | `hikaricp_connections_pending > 0`                                                                           | 0         | 30s      | Critical |
| High Error Rate           | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])` | > 5%      | 2m       | Warning  |
| Critical Error Rate       | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])` | > 10%     | 1m       | Critical |
| High Memory Usage         | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}`                                     | > 85%     | 2m       | Warning  |
| Critical Memory Usage     | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}`                                     | > 95%     | 1m       | Critical |
| Service Down              | `up == 0`                                                                                                    | 0         | 30s      | Critical |

### Business Alerts

| Alert                      | Metric                                       | Threshold | Duration | Severity |
|----------------------------|----------------------------------------------|-----------|----------|----------|
| High Concurrency Conflicts | `rate(wallet_concurrency_conflicts[5m])`     | > 10/s    | 2m       | Warning  |
| Event Store Failures       | `rate(eventstore_operations_failures[5m])`   | > 5/s     | 2m       | Warning  |
| Large Transaction Spike    | `rate(wallet_transaction_large_count[5m])`   | > 100/s   | 1m       | Warning  |
| High Failed Volume         | `rate(wallet_transaction_failed_amount[5m])` | > $100k/s | 2m       | Warning  |

## Configuration

Alert rules are defined in `observability/grafana/provisioning/alerting/rules.yaml`.

### Notification Channels

**Email**:

```yaml
- name: "email-alerts"
  type: "email"
  settings:
    addresses: "alerts@company.com"
```

**Slack**:

```yaml
- name: "slack-alerts"
  type: "slack"
  settings:
    url: "https://hooks.slack.com/services/YOUR/WEBHOOK"
    channel: "#alerts"
```

## Management

- **View Alerts**: Grafana UI → Alerting → Alert Rules
- **Acknowledge**: Click alert, add comment, acknowledge
- **Silence**: Use Grafana silence feature for maintenance

## Troubleshooting

**Alerts Not Firing**: Check metric availability in Prometheus
**False Positives**: Adjust thresholds based on historical data
**Missing Notifications**: Verify notification channel configuration

## Related Documentation

- [Observability Guide](README.md) - Overview
- [Metrics Reference](metrics-reference.md) - Available metrics
- [Dashboard Guide](dashboards.md) - Visualizing alerts
