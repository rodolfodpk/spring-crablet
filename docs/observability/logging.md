# Logging Configuration

This guide covers the structured logging configuration, log format, and querying capabilities.

## Overview

The application uses structured JSON logging with Logback, automatically shipped to Loki via Promtail for centralized log aggregation and querying.

## Log Format

### JSON Structure

```json
{
  "@timestamp": "2025-10-12T13:41:46.922844-03:00",
  "level": "DEBUG",
  "logger_name": "com.wallets.service.WalletService",
  "message": "Processing deposit for wallet: test-wallet",
  "traceId": "abc123def456",
  "spanId": "789xyz012",
  "thread": "undertow-512119",
  "logger": "c.w.s.WalletService"
}
```

### Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `@timestamp` | String | ISO 8601 timestamp | `2025-10-12T13:41:46.922844-03:00` |
| `level` | String | Log level | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `logger_name` | String | Full logger class name | `com.wallets.service.WalletService` |
| `message` | String | Log message content | `Processing deposit for wallet: test-wallet` |
| `traceId` | String | Distributed trace ID | `abc123def456` |
| `spanId` | String | Span ID within trace | `789xyz012` |
| `thread` | String | Thread name | `undertow-512119` |
| `logger` | String | Short logger name | `c.w.s.WalletService` |

### Custom Fields

- `app_name`: Application identifier (`wallet-challenge`)
- `operation`: Wallet operation type (`deposit`, `withdraw`, `transfer`)
- `wallet_id`: Wallet identifier
- `amount`: Transaction amount
- `duration`: Operation duration in milliseconds

## Log Levels

### Configuration by Environment

#### Development (`dev` profile)
```xml
<springProfile name="dev">
    <root level="DEBUG">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC_JSON_FILE"/>
    </root>
</springProfile>
```

#### Production (`default` profile)
```xml
<springProfile name="default">
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC_JSON_FILE"/>
    </root>
</springProfile>
```

### Logger-Specific Levels

```xml
<logger name="com.wallets" level="DEBUG"/>
<logger name="org.springframework.jdbc" level="INFO"/>
<logger name="com.zaxxer.hikari" level="INFO"/>
<logger name="org.flywaydb" level="DEBUG"/>
```

## Log Configuration

### Logback Configuration

The logging configuration is defined in `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <property name="LOG_FILE" value="logs/application.log"/>

    <!-- JSON Appender for file logging -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_FILE}.%d{yyyy-MM-dd}.%i.json.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <logger>logger_name</logger>
                <thread>thread</thread>
                <level>level</level>
            </fieldNames>
            <customFields>{"app_name":"wallet-challenge"}</customFields>
        </encoder>
    </appender>

    <!-- Async appender for JSON file logging -->
    <appender name="ASYNC_JSON_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="JSON_FILE"/>
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC_JSON_FILE"/>
    </root>
</configuration>
```

### Key Features

1. **Structured JSON**: All logs are in JSON format
2. **Async Appenders**: Non-blocking log writing
3. **Time-based Rolling**: Daily log rotation
4. **Size-based Rolling**: 100MB file size limit
7. **Retention**: 7 days of log history
8. **Compression**: Gzip compression for old logs

## Distributed Tracing

### Trace ID and Span ID

The application includes trace and span IDs for distributed tracing:

```java
// Example log entry with trace context
{
  "@timestamp": "2025-10-12T13:41:46.922844-03:00",
  "level": "INFO",
  "logger_name": "com.wallets.service.WalletService",
  "message": "Processing deposit for wallet: test-wallet",
  "traceId": "abc123def456",
  "spanId": "789xyz012",
  "thread": "undertow-512119"
}
```

### Trace Correlation

- **Trace ID**: Unique identifier for the entire request
- **Span ID**: Unique identifier for a specific operation
- **Parent Span**: Links spans in a trace hierarchy
- **Baggage**: Additional context data

## Log Shipping

### Promtail Configuration

Logs are automatically shipped to Loki via Promtail:

```yaml
# observability/promtail/promtail-config.yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: system
    static_configs:
      - targets:
          - localhost
        labels:
          job: wallet-challenge-logs
          __path__: /var/log/app/application.log
```

### Log Flow

1. **Application** writes logs to `logs/application.log`
2. **Promtail** monitors the log file
3. **Promtail** ships logs to **Loki**
4. **Grafana** queries logs from **Loki**

## Querying Logs in Loki

### Basic Queries

#### LogQL Syntax
```
{job="wallet-challenge-logs"} |= "error"
```

#### Common Queries

**All logs from the application:**
```
{job="wallet-challenge-logs"}
```

**Error logs only:**
```
{job="wallet-challenge-logs"} |= "ERROR"
```

**Logs from specific logger:**
```
{job="wallet-challenge-logs"} |= "WalletService"
```

**Logs with specific trace ID:**
```
{job="wallet-challenge-logs"} |= "abc123def456"
```

### Advanced Queries

#### Filter by Log Level
```
{job="wallet-challenge-logs"} | json | level="ERROR"
```

#### Filter by Operation
```
{job="wallet-challenge-logs"} | json | operation="deposit"
```

#### Filter by Wallet ID
```
{job="wallet-challenge-logs"} | json | wallet_id="test-wallet"
```

#### Filter by Duration
```
{job="wallet-challenge-logs"} | json | duration > 1000
```

### Aggregation Queries

#### Count by Log Level
```
sum by (level) (count_over_time({job="wallet-challenge-logs"} | json [5m]))
```

#### Count by Operation
```
sum by (operation) (count_over_time({job="wallet-challenge-logs"} | json [5m]))
```

#### Average Duration
```
avg_over_time({job="wallet-challenge-logs"} | json | unwrap duration [5m])
```

## Log Retention

### Retention Policy

- **Current Logs**: 7 days
- **Compressed Logs**: Gzip compression
- **Storage**: Local filesystem
- **Cleanup**: Automatic cleanup of old logs

### Configuration

```xml
<rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
    <fileNamePattern>${LOG_FILE}.%d{yyyy-MM-dd}.%i.json.gz</fileNamePattern>
    <maxHistory>7</maxHistory>
</rollingPolicy>
```

## Performance Considerations

### Async Logging

- **Queue Size**: 512 entries
- **Discarding Threshold**: 0 (no discarding)
- **Blocking**: Non-blocking log writing
- **Performance**: Minimal impact on application performance

### Log Rotation

- **Time-based**: Daily rotation
- **Size-based**: 100MB file size limit
- **Compression**: Gzip compression for old logs
- **Cleanup**: Automatic cleanup of old files

## Monitoring Log Health

### Key Metrics

- **Log Volume**: Number of log entries per time period
- **Log Size**: Size of log files
- **Error Rate**: Percentage of error logs
- **Log Latency**: Time between log generation and availability

### Alerting

- **High Error Rate**: Alert when error logs exceed threshold
- **Log Volume Spike**: Alert on unusual log volume
- **Log Shipping Failures**: Alert when Promtail fails to ship logs

## Troubleshooting

### Common Issues

1. **Logs Not Appearing**: Check Promtail configuration
2. **High Log Volume**: Adjust log levels
3. **Log Shipping Delays**: Check Promtail performance
4. **Storage Issues**: Monitor disk space

### Debugging Steps

1. **Check Log Files**: Verify logs are being written
2. **Check Promtail**: Verify Promtail is running
3. **Check Loki**: Verify Loki is receiving logs
4. **Check Queries**: Test log queries in Grafana

### Useful Commands

```bash
# Check log files
ls -la logs/

# Check Promtail logs
docker-compose logs promtail

# Check Loki logs
docker-compose logs loki

# Test log query
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={job="wallet-challenge-logs"}' \
  --data-urlencode 'start=2025-10-12T13:00:00Z' \
  --data-urlencode 'end=2025-10-12T14:00:00Z'
```

## Best Practices

### Logging Guidelines

1. **Use Appropriate Levels**: DEBUG for development, INFO for production
2. **Include Context**: Add relevant fields (wallet_id, operation, etc.)
3. **Avoid Sensitive Data**: Don't log passwords or personal information
4. **Structured Logging**: Use consistent JSON structure
5. **Performance**: Use async appenders for high-volume logging

### Query Optimization

1. **Use Filters**: Filter logs before processing
2. **Limit Time Range**: Use appropriate time ranges
3. **Use Labels**: Leverage labels for efficient querying
4. **Avoid Complex Queries**: Keep queries simple and efficient

## Related Documentation

- [Complete Observability Guide](README.md) - Overview and architecture
- [Getting Started](getting-started.md) - Setting up logging
- [Dashboard Guide](dashboards.md) - Dashboard descriptions
- [Metrics Reference](metrics-reference.md) - Available metrics
- [Alerting](alerting.md) - Log-based alerts
- [Troubleshooting](troubleshooting.md) - Common logging issues
