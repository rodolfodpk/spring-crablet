# Troubleshooting Guide

This guide covers common issues, diagnostic commands, and solutions for the observability stack.

## Common Issues

### 1. Grafana Not Loading Dashboards

#### Symptoms
- Grafana loads but shows "No data" in dashboards
- Dashboards appear empty or missing
- Error messages about datasource connectivity

#### Diagnosis
```bash
# Check if Prometheus is running
docker-compose ps prometheus

# Check Grafana logs
docker-compose logs grafana

# Check datasource configuration
curl -u admin:admin http://localhost:3000/api/datasources
```

#### Solutions
1. **Verify Prometheus is running**:
   ```bash
   docker-compose up -d prometheus
   ```

2. **Check datasource configuration**:
   - Go to Grafana → Configuration → Data Sources
   - Verify Prometheus URL: `http://prometheus:9090`
   - Test connection

3. **Restart Grafana**:
   ```bash
   docker-compose restart grafana
   ```

### 2. No Metrics Appearing

#### Symptoms
- Prometheus shows no targets
- Grafana dashboards show "No data"
- Application metrics not visible

#### Diagnosis
```bash
# Check if Spring Boot app is running
curl http://localhost:8080/actuator/health

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Check metrics endpoint
curl http://localhost:8080/actuator/prometheus
```

#### Solutions
1. **Start Spring Boot application**:
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Verify metrics endpoint**:
   - Ensure `/actuator/prometheus` is accessible
   - Check application properties for metrics configuration

3. **Check Prometheus configuration**:
   - Verify `observability/prometheus/prometheus.yml`
   - Ensure target is `host.docker.internal:8080`

### 3. Alerts Not Firing

#### Symptoms
- Alert rules configured but not triggering
- No notifications received
- Alert rules show as "inactive"

#### Diagnosis
```bash
# Check alert rules in Grafana
curl -u admin:admin http://localhost:3000/api/v1/provisioning/alert-rules

# Check alerting configuration
curl -u admin:admin http://localhost:3000/api/alerting/rules
```

#### Solutions
1. **Verify alert rules**:
   - Check `observability/grafana/provisioning/alerting/rules.yaml`
   - Ensure rules are properly formatted

2. **Check notification channels**:
   - Go to Grafana → Alerting → Notification Channels
   - Test notification channels

3. **Enable alerting**:
   - Check Grafana configuration for alerting
   - Restart Grafana after configuration changes

### 4. Logs Not Appearing in Loki

#### Symptoms
- Application logs not visible in Grafana
- Loki shows no log entries
- Promtail not shipping logs

#### Diagnosis
```bash
# Check if log files exist
ls -la logs/

# Check Promtail logs
docker-compose logs promtail

# Check Loki logs
docker-compose logs loki

# Test Loki query
curl -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={job="wallet-challenge-logs"}' \
  --data-urlencode 'start=2025-10-12T13:00:00Z' \
  --data-urlencode 'end=2025-10-12T14:00:00Z'
```

#### Solutions
1. **Check log file location**:
   - Ensure logs are written to `logs/application.log`
   - Verify file permissions

2. **Restart Promtail**:
   ```bash
   docker-compose restart promtail
   ```

3. **Check Promtail configuration**:
   - Verify `observability/promtail/promtail-config.yaml`
   - Ensure log path is correct

### 5. High Memory Usage

#### Symptoms
- JVM memory usage approaching limits
- Application performance degradation
- OutOfMemoryError exceptions

#### Diagnosis
```bash
# Check JVM memory metrics
curl http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes

# Check system memory
curl http://localhost:9090/api/v1/query?query=process_resident_memory_bytes
```

#### Solutions
1. **Increase heap size**:
   ```bash
   export JAVA_OPTS="-Xmx2g -Xms1g"
   ./mvnw spring-boot:run
   ```

2. **Monitor memory usage**:
   - Use JVM dashboard in Grafana
   - Set up memory alerts

3. **Optimize application**:
   - Check for memory leaks
   - Optimize data structures
   - Review caching strategies

### 6. Database Connection Issues

#### Symptoms
- Database connection pool exhausted
- Connection timeout errors
- Circuit breaker opening

#### Diagnosis
```bash
# Check database connection metrics
curl http://localhost:9090/api/v1/query?query=hikaricp_connections_active

# Check database health
curl http://localhost:8080/actuator/health
```

#### Solutions
1. **Increase connection pool size**:
   ```properties
   spring.datasource.hikari.maximum-pool-size=20
   spring.datasource.hikari.minimum-idle=5
   ```

2. **Check database performance**:
   - Monitor database metrics
   - Check for slow queries
   - Review connection usage

3. **Restart database**:
   ```bash
   docker-compose restart postgres
   ```

## Diagnostic Commands

### Service Health Checks

```bash
# Check all services status
docker-compose ps

# Check specific service logs
docker-compose logs grafana
docker-compose logs prometheus
docker-compose logs loki
docker-compose logs promtail

# Check service health
curl http://localhost:8080/actuator/health
curl http://localhost:9090/-/healthy
curl http://localhost:3000/api/health
curl http://localhost:3100/ready
```

### Metrics Queries

```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Query specific metrics
curl http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes
curl http://localhost:9090/api/v1/query?query=http_server_requests_seconds_count

# Check alert rules
curl http://localhost:9090/api/v1/rules
```

### Log Queries

```bash
# Query logs from Loki
curl -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={job="wallet-challenge-logs"}' \
  --data-urlencode 'start=2025-10-12T13:00:00Z' \
  --data-urlencode 'end=2025-10-12T14:00:00Z'

# Query error logs
curl -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={job="wallet-challenge-logs"} |= "ERROR"' \
  --data-urlencode 'start=2025-10-12T13:00:00Z' \
  --data-urlencode 'end=2025-10-12T14:00:00Z'
```

### Grafana API

```bash
# Check datasources
curl -u admin:admin http://localhost:3000/api/datasources

# Check dashboards
curl -u admin:admin http://localhost:3000/api/dashboards

# Check alert rules
curl -u admin:admin http://localhost:3000/api/v1/provisioning/alert-rules
```

## Performance Tuning

### Prometheus Optimization

1. **Increase scrape interval**:
   ```yaml
   global:
     scrape_interval: 30s  # Default: 15s
   ```

2. **Optimize retention**:
   ```yaml
   storage:
     retention: 15d  # Default: 15d
   ```

3. **Limit metrics**:
   ```yaml
   scrape_configs:
     - job_name: 'wallet-challenge'
       metric_relabel_configs:
         - source_labels: [__name__]
           regex: 'jvm_.*'
           action: keep
   ```

### Grafana Optimization

1. **Increase refresh interval**:
   - Set dashboard refresh to 30s or 1m
   - Use longer time ranges for historical data

2. **Optimize queries**:
   - Use rate() functions for counters
   - Limit time ranges appropriately
   - Use appropriate aggregation functions

3. **Cache configuration**:
   ```yaml
   grafana:
     environment:
       - GF_DATABASE_CACHE_TYPE=redis
       - GF_REDIS_URL=redis://redis:6379
   ```

### Loki Optimization

1. **Increase retention**:
   ```yaml
   limits_config:
     retention_period: 720h  # 30 days
   ```

2. **Optimize storage**:
   ```yaml
   storage_config:
     boltdb_shipper:
       active_index_directory: /loki/boltdb-shipper-active
       cache_location: /loki/boltdb-shipper-cache
   ```

3. **Limit log volume**:
   - Adjust log levels in production
   - Use log sampling for high-volume logs
   - Implement log filtering

## Monitoring Setup

### Key Metrics to Monitor

1. **Infrastructure**:
   - CPU usage
   - Memory usage
   - Disk space
   - Network connectivity

2. **Application**:
   - Request rate
   - Response time
   - Error rate
   - Database connections

3. **Observability Stack**:
   - Prometheus targets
   - Grafana dashboards
   - Loki log ingestion
   - Alert rules

### Alert Thresholds

1. **Critical Alerts**:
   - Service down
   - High error rate (>10%)
   - Memory usage (>95%)
   - Database pool exhausted

2. **Warning Alerts**:
   - High error rate (>5%)
   - Memory usage (>85%)
   - Database pool high (>90%)
   - High response time (>1s)

## Recovery Procedures

### Service Recovery

1. **Restart Services**:
   ```bash
   # Restart specific service
   docker-compose restart grafana
   
   # Restart all observability services
   docker-compose restart prometheus grafana loki promtail
   
   # Restart everything
   docker-compose down && docker-compose up -d
   ```

2. **Data Recovery**:
   - Prometheus data is stored in Docker volumes
   - Grafana dashboards are provisioned from files
   - Loki logs are stored in Docker volumes

3. **Configuration Recovery**:
   - All configurations are in version control
   - Restore from git if needed
   - Re-provision services after configuration changes

### Backup Procedures

1. **Configuration Backup**:
   ```bash
   # Backup observability configurations
   tar -czf observability-config-backup.tar.gz observability/
   ```

2. **Data Backup**:
   ```bash
   # Backup Prometheus data
   docker run --rm -v wallets-challenge_prometheus_data:/data -v $(pwd):/backup alpine tar czf /backup/prometheus-data-backup.tar.gz -C /data .
   ```

3. **Dashboard Backup**:
   ```bash
   # Export dashboards
   curl -u admin:admin http://localhost:3000/api/dashboards/home > dashboard-backup.json
   ```

## Related Documentation

- [Complete Observability Guide](README.md) - Overview and architecture
- [Getting Started](getting-started.md) - Initial setup
- [Dashboard Guide](dashboards.md) - Dashboard troubleshooting
- [Metrics Reference](metrics-reference.md) - Available metrics
- [Alerting](alerting.md) - Alert configuration
- [Logging](logging.md) - Log troubleshooting
