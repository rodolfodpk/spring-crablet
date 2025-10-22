# Outbox Metrics

## Available Metrics

### Throughput
- `outbox_events_published_total` - Total events published
- `outbox_events_published_by_publisher_total{publisher}` - Per-publisher count
- `rate(outbox_events_published_by_publisher_total[1m])` - Events/second

### Latency
- `outbox_publishing_duration_seconds{publisher,quantile}` - Publishing latency
- `histogram_quantile(0.95, outbox_publishing_duration_bucket)` - P95 latency

### Health
- `outbox_publishers_active` - Active publishers count
- `outbox_publishers_paused` - Paused publishers count
- `outbox_publishers_failed` - Failed publishers count
- `outbox_errors_by_publisher_total{publisher}` - Error count

### Operational
- `outbox_is_leader{instance}` - Leadership status (1=leader, 0=follower) with instance tag
- `outbox_lag` - Total lag across all publishers
- `outbox_processing_cycles_total` - Processing cycles count

## Grafana Dashboard

Import dashboard from: `observability/grafana/dashboards/outbox-monitoring.json`

### Panels
1. **Current Leader** - Shows which instance is currently the leader
2. **Events Published Rate** - Events/sec per publisher
3. **Publisher Lag** - Events behind current position
4. **Publishing Latency P95** - 95th percentile publishing time
5. **Publisher Status** - Count of active/paused/failed publishers
6. **Error Rate** - Errors/sec per publisher
7. **Leadership Changes** - How often leadership changes hands

## Prometheus Queries

```promql
# Events published per second
rate(outbox_events_published_by_publisher_total[1m])

# P95 publishing latency
histogram_quantile(0.95, rate(outbox_publishing_duration_bucket[1m]))

# Total lag
sum(outbox_lag)

# Current leader instance
outbox_is_leader == 1

# Leadership changes in last 5 minutes
changes(outbox_is_leader[5m])

# Publishers by status
outbox_publishers_active
outbox_publishers_paused
outbox_publishers_failed

# Error rate by publisher
rate(outbox_errors_by_publisher_total[1m])
```

## Instance Identification

The outbox metrics include instance identification to track which specific instance is the leader:

1. **Kubernetes**: Uses `HOSTNAME` environment variable (pod name)
2. **Custom**: Uses `crablet.instance.id` property if set
3. **Fallback**: Uses hostname or generates unique ID

The `outbox_is_leader` metric includes an `instance` tag showing the specific instance ID.

## Configuration

Enable detailed histogram metrics in `application.properties`:

```properties
# Enable detailed metrics
management.metrics.distribution.percentiles-histogram.outbox.publishing.duration=true
management.metrics.distribution.percentiles.outbox.publishing.duration=0.5,0.95,0.99
```

## Monitoring Best Practices

1. **Alert on Leadership Changes**: Set up alerts when `changes(outbox_is_leader[5m]) > 0` to detect frequent leadership changes
2. **Monitor Lag**: Alert when `outbox_lag > 1000` to detect processing delays
3. **Track Error Rates**: Alert when `rate(outbox_errors_by_publisher_total[1m]) > 0.1` for sustained errors
4. **Watch Latency**: Alert when P95 latency exceeds acceptable thresholds
5. **Instance Health**: Monitor which instance is leader and ensure it's healthy

## Troubleshooting

### High Lag
- Check if leader instance is healthy
- Verify publishers are not paused or failed
- Check database connectivity and performance

### Frequent Leadership Changes
- Indicates potential network issues or resource constraints
- Check advisory lock acquisition failures
- Monitor instance health and resource usage

### Publishing Errors
- Check publisher-specific error logs
- Verify external system connectivity (Kafka, webhooks, etc.)
- Monitor circuit breaker states
