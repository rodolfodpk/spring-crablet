# Outbox Metrics

## Available Metrics

### Throughput
- `outbox_events_published_total{publisher}` - Total events published per publisher
- `rate(outbox_events_published_total{publisher}[1m])` - Events/second per publisher

### Latency
- `outbox_publishing_duration_seconds{publisher,quantile}` - Publishing latency
- `histogram_quantile(0.95, rate(outbox_publishing_duration_seconds_bucket[1m]))` - P95 latency

### Health
- `outbox_errors_total{publisher}` - Error count per publisher
- `outbox_processing_cycles_total` - Processing cycles count

### Operational
- `processor_is_leader{processor,instance_id}` - Leadership status (1=leader, 0=follower)
- `poller_events_fetched_total{processor,instance_id}` - Events fetched per cycle
- `poller_empty_polls_total{processor,instance_id}` - Empty poll cycles

## Grafana Dashboard

Import dashboard from: `observability/grafana/dashboards/crablet-dashboard.json`

The dashboard includes an **Outbox** section with panels for:
1. **Events Published Rate** - Events/sec per publisher
2. **Publishing Latency P95** - 95th percentile publishing time
3. **Error Rate** - Errors/sec per publisher
4. **Leader Status** - `processor_is_leader` gauge per processor and instance

## Prometheus Queries

```promql
# Events published per second
rate(outbox_events_published_total[1m])

# P95 publishing latency
histogram_quantile(0.95, rate(outbox_publishing_duration_seconds_bucket[1m]))

# Current leader (instance_id tag identifies the leader)
processor_is_leader{processor="outbox"} == 1

# Leadership changes in last 5 minutes
changes(processor_is_leader{processor="outbox"}[5m])

# Error rate by publisher
rate(outbox_errors_total[1m])
```

## Instance Identification

Leadership is tracked via the `processor.is_leader` metric (Prometheus: `processor_is_leader`) with two labels:
- `processor` — the processor name (e.g. `outbox`, `views`, `automations`)
- `instance_id` — the specific instance ID (Kubernetes pod name, `crablet.instance.id`, or generated ID)

## Configuration

Enable histogram metrics in `application.properties` (required for P95 panels):

```properties
management.metrics.distribution.percentiles-histogram.outbox.publishing.duration=true
```

## Monitoring Best Practices

1. **Alert on Leadership Changes**: `changes(processor_is_leader{processor="outbox"}[5m]) > 0` detects frequent leadership instability
2. **Track Error Rates**: Alert when `rate(outbox_errors_total[1m]) > 0.1` for sustained errors
3. **Watch Latency**: Alert when P95 latency exceeds acceptable thresholds
4. **Instance Health**: Monitor which instance is leader and ensure it's healthy

## Troubleshooting

### Frequent Leadership Changes
- Indicates potential network issues or resource constraints
- Check advisory lock acquisition failures
- Monitor instance health and resource usage

### Publishing Errors
- Check publisher-specific error logs
- Verify external system connectivity (Kafka, webhooks, etc.)
- Monitor circuit breaker states
